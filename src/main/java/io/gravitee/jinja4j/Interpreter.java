/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.jinja4j;

import io.gravitee.jinja4j.ast.Node;
import io.gravitee.jinja4j.ast.Node.*;
import io.gravitee.jinja4j.ast.Node.Expr.*;
import io.gravitee.jinja4j.value.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluates a parsed Jinja2 AST to produce rendered output.
 */
public final class Interpreter {

  private final Environment env;

  public Interpreter(Environment env) {
    this.env = env;
  }

  public String render(Node.Template template, RenderContext ctx) {
    var sb = new StringBuilder();

    // Check if this template has an extends node
    boolean hasExtends = template
      .body()
      .stream()
      .anyMatch(n -> n instanceof ExtendsNode);

    if (hasExtends) {
      // In extends mode: collect blocks from child, then process only the extends node
      for (var node : template.body()) {
        if (node instanceof BlockNode(var name, var body, _)) {
          ctx.setBlock(name, body);
        } else if (node instanceof ExtendsNode) {
          executeNode(node, ctx, sb);
        }
        // Skip Text/Output nodes in child template with extends
      }
    } else {
      executeBody(template.body(), ctx, sb);
    }

    return sb.toString();
  }

  private void executeBody(List<Node> body, RenderContext ctx, StringBuilder sb) {
    for (var node : body) {
      executeNode(node, ctx, sb);
    }
  }

  private void executeNode(Node node, RenderContext ctx, StringBuilder sb) {
    switch (node) {
      case Text(var value, _) -> sb.append(value);
      case Output(var expr, var loc) -> {
        var val = evalExpr(expr, ctx);
        if (val instanceof Value.Undefined) {
          if (env.isUndefinedBehaviorStrict()) {
            throw new TemplateException("Undefined value in output", loc);
          }
          // Render nothing for undefined
        } else {
          var str = val.asString();
          if (env.isAutoEscaping() && !(val instanceof Value.SafeStringVal)) {
            str = htmlEscape(str);
          }
          sb.append(str);
        }
      }
      case IfNode(var branches, var elseBranch, _) -> {
        boolean matched = false;
        for (var branch : branches) {
          var condVal = evalExpr(branch.condition(), ctx);
          if (condVal.isTruthy()) {
            executeBody(branch.body(), ctx, sb);
            matched = true;
            break;
          }
        }
        if (!matched) {
          executeBody(elseBranch, ctx, sb);
        }
      }
      case ForNode(var target, var unpackTargets, var iterableExpr, var body, var elseBranch, _, var loc) -> {
        var iterableVal = evalExpr(iterableExpr, ctx);
        var items = toIterable(iterableVal, loc);
        if (items.isEmpty()) {
          executeBody(elseBranch, ctx, sb);
        } else {
          int length = items.size();
          for (int i = 0; i < length; i++) {
            var scope = ctx.pushScope();
            var item = items.get(i);
            if (target != null) {
              scope.set(target, item);
            } else if (unpackTargets != null) {
              unpackInto(item, unpackTargets, scope, loc);
            }
            // Loop variable
            var loopMap = new LinkedHashMap<String, Value>();
            loopMap.put("index", Value.of(i + 1));
            loopMap.put("index0", Value.of(i));
            loopMap.put("first", Value.of(i == 0));
            loopMap.put("last", Value.of(i == length - 1));
            loopMap.put("length", Value.of(length));
            loopMap.put("revindex", Value.of(length - i));
            loopMap.put("revindex0", Value.of(length - i - 1));
            // previtem / nextitem — Jinja2 loop helpers for accessing adjacent items
            loopMap.put("previtem", i > 0 ? items.get(i - 1) : Value.UNDEFINED);
            loopMap.put("nextitem", i < length - 1 ? items.get(i + 1) : Value.UNDEFINED);
            // Jinja2 also exposes loop.cycle as a callable
            int finalI = i;
            loopMap.put(
              "cycle",
              Value.ofCallable("loop.cycle", (args, kwargs) -> {
                if (args.isEmpty()) return Value.UNDEFINED;
                return args.get(finalI % args.size());
              })
            );
            scope.set("loop", Value.ofMap(loopMap));
            executeBody(body, scope, sb);
            ctx.popScope();
          }
        }
      }
      case SetNode(var target, var valueExpr, _) -> {
        var val = evalExpr(valueExpr, ctx);
        ctx.set(target, val);
      }
      case SetAttrNode(var nsName, var attr, var valueExpr, var loc) -> {
        var nsVal = ctx.resolve(nsName);
        if (nsVal instanceof Value.NamespaceVal nv) {
          nv.ns().set(attr, evalExpr(valueExpr, ctx));
        } else {
          throw new TemplateException("Cannot set attribute on non-namespace '%s'".formatted(nsName), loc);
        }
      }
      case BlockNode(var name, var body, _) -> {
        // If we're in extends mode, blocks are collected separately
        // During normal rendering, just execute the body
        var overridden = ctx.getBlock(name);
        if (overridden != null) {
          executeBody(overridden, ctx, sb);
        } else {
          executeBody(body, ctx, sb);
        }
      }
      case ExtendsNode(var templateExpr, var loc) -> {
        var parentName = evalExpr(templateExpr, ctx).asString();
        // Parse the parent template
        var parentSource = env.loadTemplate(parentName);
        if (parentSource == null) {
          throw new TemplateException("Template '%s' not found".formatted(parentName), loc);
        }
        var lexer = new Lexer(parentSource, parentName);
        var tokens = lexer.tokenizeAndTrim();
        var parser = new Parser(tokens, parentName);
        var parentAst = parser.parse();
        // Render the parent with the child's blocks available via render()
        // which handles extends recursion
        sb.append(render(parentAst, ctx));
      }
      case IncludeNode(var templateExpr, var ignoreMissing, var loc) -> {
        var includeName = evalExpr(templateExpr, ctx).asString();
        var includeSource = env.loadTemplate(includeName);
        if (includeSource == null) {
          if (ignoreMissing) return;
          throw new TemplateException("Template '%s' not found".formatted(includeName), loc);
        }
        var lexer = new Lexer(includeSource, includeName);
        var tokens = lexer.tokenizeAndTrim();
        var parser = new Parser(tokens, includeName);
        var includeAst = parser.parse();
        executeBody(includeAst.body(), ctx, sb);
      }
      case MacroNode(var name, var args, var body, var loc) -> {
        // Register the macro as a callable in the current scope
        ctx.set(
          name,
          Value.ofCallable(name, (callArgs, callKwargs) -> {
            var macroScope = ctx.pushScope();
            // Bind arguments
            for (int i = 0; i < args.size(); i++) {
              var arg = args.get(i);
              Value val;
              if (callKwargs.containsKey(arg.name())) {
                val = callKwargs.get(arg.name());
              } else if (i < callArgs.size()) {
                val = callArgs.get(i);
              } else if (arg.defaultValue() != null) {
                val = evalExpr(arg.defaultValue(), macroScope);
              } else {
                val = Value.UNDEFINED;
              }
              macroScope.set(arg.name(), val);
            }
            var macroSb = new StringBuilder();
            executeBody(body, macroScope, macroSb);
            ctx.popScope();
            return Value.of(macroSb.toString());
          })
        );
      }
      case WithNode(var assignments, var body, _) -> {
        var scope = ctx.pushScope();
        for (var assignment : assignments) {
          scope.set(assignment.target(), evalExpr(assignment.value(), scope));
        }
        executeBody(body, scope, sb);
        ctx.popScope();
      }
      case GenerationNode(_) -> {
        // No-op marker — just continue
      }
      case FilterBlockNode(var filterName, var args, var body, var loc) -> {
        var innerSb = new StringBuilder();
        executeBody(body, ctx, innerSb);
        var innerVal = Value.of(innerSb.toString());
        var filteredArgs = new ArrayList<Value>();
        for (var a : args) filteredArgs.add(evalExpr(a, ctx));
        var result = env.applyFilter(filterName, innerVal, filteredArgs, Map.of(), loc);
        sb.append(result.asString());
      }
      case RawNode(var content, _) -> sb.append(content);
      case Node.Template(var body, _) -> executeBody(body, ctx, sb);
      default -> {
        // Expression nodes shouldn't appear at statement level normally
      }
    }
  }

  // ---- Expression evaluation ----

  public Value evalExpr(Expr expr, RenderContext ctx) {
    return switch (expr) {
      case Literal(var value, _) -> value;
      case Name(var name, _) -> ctx.resolve(name);
      case GetAttr(var object, var attribute, var loc) -> {
        var obj = evalExpr(object, ctx);
        yield getAttribute(obj, attribute, loc);
      }
      case GetItem(var object, var key, var loc) -> {
        var obj = evalExpr(object, ctx);
        var keyVal = evalExpr(key, ctx);
        yield getItem(obj, keyVal, loc);
      }
      case BinOp(var left, var op, var right, var loc) -> evalBinOp(left, op, right, ctx, loc);
      case UnaryOp(var op, var operand, var loc) -> {
        var val = evalExpr(operand, ctx);
        yield switch (op) {
          case NEG -> {
            if (val instanceof Value.IntVal iv) yield Value.of(-iv.value());
            if (val instanceof Value.FloatVal fv) yield Value.of(-fv.value());
            throw new TemplateException("Cannot negate %s".formatted(val), loc);
          }
          case NOT -> Value.of(!val.isTruthy());
        };
      }
      case Ternary(var trueExpr, var condition, var falseExpr, _) -> {
        var cond = evalExpr(condition, ctx);
        yield cond.isTruthy() ? evalExpr(trueExpr, ctx) : evalExpr(falseExpr, ctx);
      }
      case Filter(var value, var name, var args, var kwargs, var loc) -> {
        var val = evalExpr(value, ctx);
        var evalArgs = new ArrayList<Value>();
        for (var a : args) evalArgs.add(evalExpr(a, ctx));
        var evalKwargs = new LinkedHashMap<String, Value>();
        for (var e : kwargs.entrySet()) evalKwargs.put(e.getKey(), evalExpr(e.getValue(), ctx));
        yield env.applyFilter(name, val, evalArgs, evalKwargs, loc);
      }
      case Test(var value, var name, var args, var negated, var loc) -> {
        var val = evalExpr(value, ctx);
        var evalArgs = new ArrayList<Value>();
        for (var a : args) evalArgs.add(evalExpr(a, ctx));
        var result = env.applyTest(name, val, evalArgs, loc);
        yield Value.of(negated != result);
      }
      case Call(var callee, var args, var kwargs, var loc) -> {
        var fn = evalExpr(callee, ctx);
        var evalArgs = new ArrayList<Value>();
        for (var a : args) evalArgs.add(evalExpr(a, ctx));
        var evalKwargs = new LinkedHashMap<String, Value>();
        for (var e : kwargs.entrySet()) evalKwargs.put(e.getKey(), evalExpr(e.getValue(), ctx));
        if (fn instanceof Value.CallableVal cv) {
          yield cv.fn().call(evalArgs, evalKwargs);
        }
        throw new TemplateException("'%s' is not callable".formatted(fn), loc);
      }
      case Concat(var parts, _) -> {
        var sb = new StringBuilder();
        for (var part : parts) sb.append(evalExpr(part, ctx).asString());
        yield Value.of(sb.toString());
      }
      case ListLiteral(var items, _) -> {
        var list = new ArrayList<Value>();
        for (var item : items) list.add(evalExpr(item, ctx));
        yield Value.ofList(list);
      }
      case DictLiteral(var entries, _) -> {
        var map = new LinkedHashMap<String, Value>();
        for (var entry : entries) {
          var key = evalExpr(entry.getKey(), ctx);
          var val = evalExpr(entry.getValue(), ctx);
          map.put(key.asString(), val);
        }
        yield Value.ofMap(map);
      }
      case TupleLiteral(var items, _) -> {
        var list = new ArrayList<Value>();
        for (var item : items) list.add(evalExpr(item, ctx));
        yield Value.ofList(list);
      }
      case Slice(var object, var start, var stop, var step, var loc) -> {
        var obj = evalExpr(object, ctx);
        yield evalSlice(obj, start, stop, step, ctx, loc);
      }
    };
  }

  // ---- Binary operators ----

  private Value evalBinOp(Expr leftExpr, Node.BinOperator op, Expr rightExpr, RenderContext ctx, SourceLocation loc) {
    // Short-circuit for AND/OR
    if (op == Node.BinOperator.AND) {
      var left = evalExpr(leftExpr, ctx);
      return left.isTruthy() ? evalExpr(rightExpr, ctx) : left;
    }
    if (op == Node.BinOperator.OR) {
      var left = evalExpr(leftExpr, ctx);
      return left.isTruthy() ? left : evalExpr(rightExpr, ctx);
    }

    var left = evalExpr(leftExpr, ctx);
    var right = evalExpr(rightExpr, ctx);

    return switch (op) {
      case ADD -> add(left, right, loc);
      case SUB -> arithmetic(left, right, (a, b) -> a - b, (a, b) -> a - b, loc);
      case MUL -> mul(left, right, loc);
      case DIV -> {
        // Division always returns float in Jinja2
        yield Value.of(left.asDouble() / right.asDouble());
      }
      case FLOOR_DIV -> {
        if (left instanceof Value.IntVal li && right instanceof Value.IntVal ri) {
          yield Value.of(Math.floorDiv(li.value(), ri.value()));
        }
        yield Value.of((long) Math.floor(left.asDouble() / right.asDouble()));
      }
      case MOD -> {
        if (left instanceof Value.IntVal li && right instanceof Value.IntVal ri) {
          yield Value.of(Math.floorMod(li.value(), ri.value()));
        }
        yield Value.of(left.asDouble() % right.asDouble());
      }
      case POW -> {
        if (left instanceof Value.IntVal li && right instanceof Value.IntVal ri && ri.value() >= 0) {
          yield Value.of((long) Math.pow(li.value(), ri.value()));
        }
        yield Value.of(Math.pow(left.asDouble(), right.asDouble()));
      }
      case EQ -> Value.of(valueEquals(left, right));
      case NE -> Value.of(!valueEquals(left, right));
      case LT -> Value.of(compareValues(left, right, loc) < 0);
      case GT -> Value.of(compareValues(left, right, loc) > 0);
      case LE -> Value.of(compareValues(left, right, loc) <= 0);
      case GE -> Value.of(compareValues(left, right, loc) >= 0);
      case IN -> Value.of(containsValue(right, left, loc));
      case NOT_IN -> Value.of(!containsValue(right, left, loc));
      case CONCAT -> Value.of(left.asString() + right.asString());
      case AND, OR -> throw new IllegalStateException("Should be handled above");
    };
  }

  private Value add(Value left, Value right, SourceLocation loc) {
    // String concatenation with +
    if (left instanceof Value.StringVal ls && right instanceof Value.StringVal rs) {
      return Value.of(ls.value() + rs.value());
    }
    if (left instanceof Value.ListVal ll && right instanceof Value.ListVal rl) {
      var combined = new ArrayList<>(ll.items());
      combined.addAll(rl.items());
      return Value.ofList(combined);
    }
    return arithmetic(left, right, Long::sum, Double::sum, loc);
  }

  private Value mul(Value left, Value right, SourceLocation loc) {
    // String * int repetition
    if (left instanceof Value.StringVal ls && right instanceof Value.IntVal ri) {
      return Value.of(ls.value().repeat((int) ri.value()));
    }
    if (left instanceof Value.IntVal li && right instanceof Value.StringVal rs) {
      return Value.of(rs.value().repeat((int) li.value()));
    }
    return arithmetic(left, right, (a, b) -> a * b, (a, b) -> a * b, loc);
  }

  @FunctionalInterface
  private interface LongBiOp {
    long apply(long a, long b);
  }

  @FunctionalInterface
  private interface DoubleBiOp {
    double apply(double a, double b);
  }

  private Value arithmetic(Value left, Value right, LongBiOp longOp, DoubleBiOp doubleOp, SourceLocation loc) {
    if (left instanceof Value.IntVal li && right instanceof Value.IntVal ri) {
      return Value.of(longOp.apply(li.value(), ri.value()));
    }
    try {
      return Value.of(doubleOp.apply(left.asDouble(), right.asDouble()));
    } catch (Exception e) {
      throw new TemplateException("Cannot perform arithmetic on %s and %s".formatted(left, right), loc);
    }
  }

  private boolean valueEquals(Value left, Value right) {
    if (left instanceof Value.Undefined && right instanceof Value.Undefined) return true;
    if (left instanceof Value.NullVal && right instanceof Value.NullVal) return true;
    if (left instanceof Value.NullVal && right instanceof Value.Undefined) return false;
    if (left instanceof Value.Undefined && right instanceof Value.NullVal) return false;
    if (left instanceof Value.BoolVal lb && right instanceof Value.BoolVal rb) return lb.value() == rb.value();
    if (left instanceof Value.StringVal ls && right instanceof Value.StringVal rs) return ls.value().equals(rs.value());
    if (left instanceof Value.SafeStringVal lss && right instanceof Value.StringVal rs) return lss
      .value()
      .equals(rs.value());
    if (left instanceof Value.StringVal ls && right instanceof Value.SafeStringVal rss) return ls
      .value()
      .equals(rss.value());
    if (left.isNumber() && right.isNumber()) {
      if (left instanceof Value.IntVal li && right instanceof Value.IntVal ri) return li.value() == ri.value();
      return left.asDouble() == right.asDouble();
    }
    if (left instanceof Value.ListVal ll && right instanceof Value.ListVal rl) {
      var l1 = ll.items();
      var l2 = rl.items();
      if (l1.size() != l2.size()) return false;
      for (int i = 0; i < l1.size(); i++) {
        if (!valueEquals(l1.get(i), l2.get(i))) return false;
      }
      return true;
    }
    if (left instanceof Value.MapVal lm && right instanceof Value.MapVal rm) {
      var m1 = lm.entries();
      var m2 = rm.entries();
      if (m1.size() != m2.size()) return false;
      for (var e : m1.entrySet()) {
        var v2 = m2.get(e.getKey());
        if (v2 == null || !valueEquals(e.getValue(), v2)) return false;
      }
      return true;
    }
    return false;
  }

  private int compareValues(Value left, Value right, SourceLocation loc) {
    if (left.isNumber() && right.isNumber()) {
      return Double.compare(left.asDouble(), right.asDouble());
    }
    if (left instanceof Value.StringVal ls && right instanceof Value.StringVal rs) {
      return ls.value().compareTo(rs.value());
    }
    throw new TemplateException("Cannot compare %s and %s".formatted(left, right), loc);
  }

  private boolean containsValue(Value container, Value item, SourceLocation loc) {
    return switch (container) {
      case Value.ListVal lv -> {
        for (var v : lv.items()) if (valueEquals(v, item)) yield true;
        yield false;
      }
      case Value.MapVal mv -> {
        var entries = mv.entries();
        if (item instanceof Value.StringVal sv) yield entries.containsKey(sv.value());
        yield entries.containsKey(item.asString());
      }
      case Value.StringVal sv -> sv.value().contains(item.asString());
      case Value.SafeStringVal ssv -> ssv.value().contains(item.asString());
      default -> throw new TemplateException("Cannot test membership in %s".formatted(container), loc);
    };
  }

  // ---- Attribute / item access ----

  private Value getAttribute(Value obj, String attribute, SourceLocation loc) {
    return switch (obj) {
      case Value.MapVal mv -> getMapAttribute(mv.entries(), attribute, loc);
      case Value.NamespaceVal nv -> nv.ns().get(attribute);
      case Value.StringVal sv -> getStringMethod(sv.value(), attribute, loc);
      case Value.SafeStringVal ssv -> getStringMethod(ssv.value(), attribute, loc);
      case Value.ListVal lv -> getListAttribute(lv.items(), attribute, loc);
      case Value.Undefined ignored -> Value.UNDEFINED;
      default -> Value.UNDEFINED;
    };
  }

  /**
   * Expose Python-like string methods as callable attributes.
   */
  private Value getStringMethod(String s, String method, SourceLocation loc) {
    return switch (method) {
      case "startswith" -> Value.ofCallable("str.startswith", (args, kw) -> {
        if (args.isEmpty()) return Value.FALSE;
        return Value.of(s.startsWith(args.getFirst().asString()));
      });
      case "endswith" -> Value.ofCallable("str.endswith", (args, kw) -> {
        if (args.isEmpty()) return Value.FALSE;
        return Value.of(s.endsWith(args.getFirst().asString()));
      });
      case "split" -> Value.ofCallable("str.split", (args, kw) -> {
        var sep = !args.isEmpty() ? args.getFirst().asString() : null;
        String[] parts;
        if (sep == null) {
          parts = s.strip().split("\\s+");
        } else {
          parts = s.split(java.util.regex.Pattern.quote(sep), -1);
        }
        var list = new ArrayList<Value>();
        for (var p : parts) list.add(Value.of(p));
        return Value.ofList(list);
      });
      case "strip" -> Value.ofCallable("str.strip", (args, kw) -> {
        if (!args.isEmpty()) {
          var chars = args.getFirst().asString();
          return Value.of(stripChars(s, chars, true, true));
        }
        return Value.of(s.strip());
      });
      case "lstrip" -> Value.ofCallable("str.lstrip", (args, kw) -> {
        if (!args.isEmpty()) {
          var chars = args.getFirst().asString();
          return Value.of(stripChars(s, chars, true, false));
        }
        return Value.of(s.stripLeading());
      });
      case "rstrip" -> Value.ofCallable("str.rstrip", (args, kw) -> {
        if (!args.isEmpty()) {
          var chars = args.getFirst().asString();
          return Value.of(stripChars(s, chars, false, true));
        }
        return Value.of(s.stripTrailing());
      });
      case "upper" -> Value.ofCallable("str.upper", (args, kw) -> Value.of(s.toUpperCase()));
      case "lower" -> Value.ofCallable("str.lower", (args, kw) -> Value.of(s.toLowerCase()));
      case "title" -> Value.ofCallable("str.title", (args, kw) -> {
        var sb = new StringBuilder();
        boolean nextUpper = true;
        for (var c : s.toCharArray()) {
          if (!Character.isLetterOrDigit(c)) {
            sb.append(c);
            nextUpper = true;
          } else if (nextUpper) {
            sb.append(Character.toUpperCase(c));
            nextUpper = false;
          } else {
            sb.append(Character.toLowerCase(c));
          }
        }
        return Value.of(sb.toString());
      });
      case "replace" -> Value.ofCallable("str.replace", (args, kw) -> {
        if (args.size() < 2) throw new TemplateException("str.replace() requires 2 arguments", loc);
        return Value.of(s.replace(args.get(0).asString(), args.get(1).asString()));
      });
      case "find" -> Value.ofCallable("str.find", (args, kw) -> {
        if (args.isEmpty()) return Value.of(-1);
        return Value.of(s.indexOf(args.getFirst().asString()));
      });
      case "count" -> Value.ofCallable("str.count", (args, kw) -> {
        if (args.isEmpty()) return Value.of(0);
        var sub = args.getFirst().asString();
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(sub, idx)) >= 0) {
          count++;
          idx += sub.length();
        }
        return Value.of(count);
      });
      case "join" -> Value.ofCallable("str.join", (args, kw) -> {
        if (args.isEmpty()) return Value.of("");
        if (args.getFirst() instanceof Value.ListVal argLv) {
          return Value.of(argLv.items().stream().map(Value::asString).collect(Collectors.joining(s)));
        }
        return Value.of("");
      });
      case "format" -> Value.ofCallable("str.format", (args, kw) -> {
        // Basic positional only
        var result = s;
        for (int i = 0; i < args.size(); i++) {
          result = result.replace("{" + i + "}", args.get(i).asString());
        }
        for (var e : kw.entrySet()) {
          result = result.replace("{" + e.getKey() + "}", e.getValue().asString());
        }
        return Value.of(result);
      });
      default -> Value.UNDEFINED;
    };
  }

  /**
   * Strip specific characters from left/right of a string (Python-style).
   */
  private static String stripChars(String s, String chars, boolean left, boolean right) {
    int start = 0;
    int end = s.length();
    if (left) {
      while (start < end && chars.indexOf(s.charAt(start)) >= 0) start++;
    }
    if (right) {
      while (end > start && chars.indexOf(s.charAt(end - 1)) >= 0) end--;
    }
    return s.substring(start, end);
  }

  /**
   * List attribute access — supports length-like properties.
   */
  private Value getListAttribute(List<Value> items, String attribute, SourceLocation loc) {
    return switch (attribute) {
      case "append" -> Value.ofCallable("list.append", (args, kw) -> {
        if (!args.isEmpty()) {
          var mutable = new ArrayList<>(items);
          mutable.add(args.getFirst());
          return Value.ofList(mutable);
        }
        return Value.ofList(items);
      });
      case "length" -> Value.of(items.size());
      default -> Value.UNDEFINED;
    };
  }

  /**
   * Map (dict) attribute access. Exposes Python dict methods like .get(), .keys(), .values(), .items().
   * Regular key lookups fall through to the entries map.
   */
  private Value getMapAttribute(Map<String, Value> entries, String attribute, SourceLocation loc) {
    return switch (attribute) {
      case "get" -> Value.ofCallable("dict.get", (args, kw) -> {
        if (args.isEmpty()) return Value.UNDEFINED;
        var key = args.getFirst().asString();
        var defaultVal = args.size() > 1 ? args.get(1) : Value.NULL;
        return entries.getOrDefault(key, defaultVal);
      });
      case "keys" -> Value.ofCallable("dict.keys", (args, kw) ->
        Value.ofList(entries.keySet().stream().map(Value::of).toList())
      );
      case "values" -> Value.ofCallable("dict.values", (args, kw) -> Value.ofList(new ArrayList<>(entries.values())));
      case "items" -> Value.ofCallable("dict.items", (args, kw) -> {
        var items = new ArrayList<Value>();
        for (var e : entries.entrySet()) {
          items.add(Value.ofList(List.of(Value.of(e.getKey()), e.getValue())));
        }
        return Value.ofList(items);
      });
      case "update" -> Value.ofCallable("dict.update", (args, kw) -> {
        if (!args.isEmpty() && args.getFirst() instanceof Value.MapVal otherMv) {
          entries.putAll(otherMv.entries());
        }
        entries.putAll(kw);
        return Value.NULL;
      });
      default -> entries.getOrDefault(attribute, Value.UNDEFINED);
    };
  }

  private Value getItem(Value obj, Value key, SourceLocation loc) {
    return switch (obj) {
      case Value.MapVal mv -> {
        var entries = mv.entries();
        var keyStr = key.asString();
        yield entries.getOrDefault(keyStr, Value.UNDEFINED);
      }
      case Value.NamespaceVal nv -> nv.ns().get(key.asString());
      case Value.ListVal lv -> {
        var items = lv.items();
        if (key instanceof Value.IntVal iv) {
          int idx = (int) iv.value();
          if (idx < 0) idx = items.size() + idx;
          if (idx >= 0 && idx < items.size()) yield items.get(idx);
          yield Value.UNDEFINED;
        }
        yield Value.UNDEFINED;
      }
      case Value.StringVal sv -> {
        var s = sv.value();
        if (key instanceof Value.IntVal iv) {
          int idx = (int) iv.value();
          if (idx < 0) idx = s.length() + idx;
          if (idx >= 0 && idx < s.length()) yield Value.of(String.valueOf(s.charAt(idx)));
        }
        yield Value.UNDEFINED;
      }
      case Value.Undefined ignored -> Value.UNDEFINED;
      default -> Value.UNDEFINED;
    };
  }

  private Value evalSlice(Value obj, Expr startExpr, Expr stopExpr, Expr stepExpr, RenderContext ctx, SourceLocation loc) {
    if (obj instanceof Value.ListVal objLv) {
      var items = objLv.items();
      int len = items.size();
      int step = stepExpr != null ? (int) evalExpr(stepExpr, ctx).asLong() : 1;
      // Python-style defaults: positive step → start=0,stop=len; negative step → start=len-1,stop=before-beginning
      int start, stop;
      if (step > 0) {
        start = startExpr != null ? normalizeIndex((int) evalExpr(startExpr, ctx).asLong(), len) : 0;
        stop = stopExpr != null ? normalizeIndex((int) evalExpr(stopExpr, ctx).asLong(), len) : len;
      } else {
        start = startExpr != null ? normalizeIndex((int) evalExpr(startExpr, ctx).asLong(), len) : len - 1;
        stop = stopExpr != null ? normalizeNegStop((int) evalExpr(stopExpr, ctx).asLong(), len) : -1;
      }
      var result = new ArrayList<Value>();
      if (step > 0) {
        for (int i = start; i < stop; i += step) result.add(items.get(i));
      } else if (step < 0) {
        for (int i = start; i > stop; i += step) result.add(items.get(i));
      }
      return Value.ofList(result);
    }
    if (obj instanceof Value.StringVal objSv) {
      var s = objSv.value();
      int len = s.length();
      int step = stepExpr != null ? (int) evalExpr(stepExpr, ctx).asLong() : 1;
      int start, stop;
      if (step > 0) {
        start = startExpr != null ? normalizeIndex((int) evalExpr(startExpr, ctx).asLong(), len) : 0;
        stop = stopExpr != null ? normalizeIndex((int) evalExpr(stopExpr, ctx).asLong(), len) : len;
      } else {
        start = startExpr != null ? normalizeIndex((int) evalExpr(startExpr, ctx).asLong(), len) : len - 1;
        stop = stopExpr != null ? normalizeNegStop((int) evalExpr(stopExpr, ctx).asLong(), len) : -1;
      }
      var sb = new StringBuilder();
      if (step > 0) {
        for (int i = start; i < stop; i += step) sb.append(s.charAt(i));
      } else if (step < 0) {
        for (int i = start; i > stop; i += step) sb.append(s.charAt(i));
      }
      return Value.of(sb.toString());
    }
    throw new TemplateException("Cannot slice %s".formatted(obj), loc);
  }

  private static int normalizeIndex(int idx, int len) {
    if (idx < 0) idx = len + idx;
    return Math.max(0, Math.min(idx, len));
  }

  /** For negative-step stop: allows -1 to mean "before the first element". */
  private static int normalizeNegStop(int idx, int len) {
    if (idx < 0) idx = len + idx;
    return Math.max(-1, Math.min(idx, len));
  }

  // ---- Helpers ----

  private List<Value> toIterable(Value val, SourceLocation loc) {
    return switch (val) {
      case Value.ListVal lv -> lv.items();
      case Value.MapVal mv -> {
        var entries = mv.entries();
        var list = new ArrayList<Value>();
        for (var key : entries.keySet()) list.add(Value.of(key));
        yield list;
      }
      case Value.StringVal sv -> {
        var s = sv.value();
        var list = new ArrayList<Value>();
        for (var c : s.toCharArray()) list.add(Value.of(String.valueOf(c)));
        yield list;
      }
      case Value.Undefined ignored -> List.of();
      case Value.NullVal ignored -> List.of();
      default -> throw new TemplateException("Cannot iterate over %s".formatted(val), loc);
    };
  }

  private void unpackInto(Value item, List<String> targets, RenderContext scope, SourceLocation loc) {
    if (item instanceof Value.ListVal itLv) {
      var items = itLv.items();
      for (int i = 0; i < targets.size(); i++) {
        scope.set(targets.get(i), i < items.size() ? items.get(i) : Value.UNDEFINED);
      }
    } else if (item instanceof Value.MapVal itMv) {
      // When iterating over dict items() result, we get [key, value] pairs
      var keys = new ArrayList<>(itMv.entries().keySet());
      for (int i = 0; i < targets.size(); i++) {
        scope.set(targets.get(i), i < keys.size() ? Value.of(keys.get(i)) : Value.UNDEFINED);
      }
    } else {
      throw new TemplateException("Cannot unpack %s into %d variables".formatted(item, targets.size()), loc);
    }
  }

  static String htmlEscape(String s) {
    var sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&#34;");
        case '\'' -> sb.append("&#39;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
