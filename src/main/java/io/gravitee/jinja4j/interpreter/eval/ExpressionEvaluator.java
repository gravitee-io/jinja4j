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
package io.gravitee.jinja4j.interpreter.eval;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.RenderContext;
import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.ast.Node;
import io.gravitee.jinja4j.ast.Node.Expr;
import io.gravitee.jinja4j.ast.Node.Expr.*;
import io.gravitee.jinja4j.interpreter.access.ValueAccessor;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates {@link Expr} AST nodes to {@link Value}.
 *
 * <p>Responsible for the pure, output-free half of interpretation:
 * name resolution, attribute/item access, arithmetic and comparisons,
 * filters, tests, calls, literal construction, and slicing.</p>
 *
 * <p>Short-circuiting for {@code and}/{@code or} is handled at the top
 * of {@link #evalBinOp} so that the right-hand side is never evaluated
 * when the left-hand side already determines the result.</p>
 */
public final class ExpressionEvaluator {

  private final Environment env;

  public ExpressionEvaluator(Environment env) {
    this.env = env;
  }

  public Value eval(Expr expr, RenderContext ctx) {
    return switch (expr) {
      case Literal(var value, _) -> value;
      case Name(var name, _) -> ctx.resolve(name);
      case GetAttr(var object, var attribute, var loc) -> ValueAccessor.getAttribute(eval(object, ctx), attribute, loc);
      case GetItem(var object, var key, var loc) -> ValueAccessor.getItem(eval(object, ctx), eval(key, ctx), loc);
      case BinOp(var left, var op, var right, var loc) -> evalBinOp(left, op, right, ctx, loc);
      case UnaryOp(var op, var operand, var loc) -> evalUnary(op, operand, ctx, loc);
      case Ternary(var trueExpr, var condition, var falseExpr, _) -> eval(condition, ctx).isTruthy()
        ? eval(trueExpr, ctx)
        : eval(falseExpr, ctx);
      case Filter(var value, var name, var args, var kwargs, var loc) -> env.applyFilter(
        name,
        eval(value, ctx),
        evalArgs(args, ctx),
        evalKwargs(kwargs, ctx),
        loc
      );
      case Test(var value, var name, var args, var negated, var loc) -> {
        var result = env.applyTest(name, eval(value, ctx), evalArgs(args, ctx), loc);
        yield Value.of(negated != result);
      }
      case Call(var callee, var args, var kwargs, var loc) -> evalCall(
        eval(callee, ctx),
        evalArgs(args, ctx),
        evalKwargs(kwargs, ctx),
        loc
      );
      case Concat(var parts, _) -> concat(parts, ctx);
      case ListLiteral(var items, _) -> Value.ofList(evalArgs(items, ctx));
      case TupleLiteral(var items, _) -> Value.ofList(evalArgs(items, ctx));
      case DictLiteral(var entries, _) -> evalDictLiteral(entries, ctx);
      case Slice(var object, var start, var stop, var step, var loc) -> evalSlice(object, start, stop, step, ctx, loc);
    };
  }

  // ---- Binary operators ----

  private Value evalBinOp(Expr leftExpr, Node.BinOperator op, Expr rightExpr, RenderContext ctx, SourceLocation loc) {
    // Short-circuit before evaluating the right-hand side
    if (op == Node.BinOperator.AND) {
      var left = eval(leftExpr, ctx);
      return left.isTruthy() ? eval(rightExpr, ctx) : left;
    }
    if (op == Node.BinOperator.OR) {
      var left = eval(leftExpr, ctx);
      return left.isTruthy() ? left : eval(rightExpr, ctx);
    }

    var left = eval(leftExpr, ctx);
    var right = eval(rightExpr, ctx);

    return switch (op) {
      case ADD -> Arithmetic.add(left, right, loc);
      case SUB -> Arithmetic.sub(left, right, loc);
      case MUL -> Arithmetic.mul(left, right, loc);
      case DIV -> Arithmetic.div(left, right);
      case FLOOR_DIV -> Arithmetic.floorDiv(left, right);
      case MOD -> Arithmetic.mod(left, right);
      case POW -> Arithmetic.pow(left, right);
      case EQ -> Value.of(ValueRelations.equals(left, right));
      case NE -> Value.of(!ValueRelations.equals(left, right));
      case LT -> Value.of(ValueRelations.compare(left, right, loc) < 0);
      case GT -> Value.of(ValueRelations.compare(left, right, loc) > 0);
      case LE -> Value.of(ValueRelations.compare(left, right, loc) <= 0);
      case GE -> Value.of(ValueRelations.compare(left, right, loc) >= 0);
      case IN -> Value.of(ValueRelations.contains(right, left, loc));
      case NOT_IN -> Value.of(!ValueRelations.contains(right, left, loc));
      case CONCAT -> Value.of(left.asString() + right.asString());
      case AND, OR -> throw new IllegalStateException("Short-circuited above");
    };
  }

  // ---- Unary / Call / Concat / DictLiteral / Slice ----

  private Value evalUnary(Node.UnaryOperator op, Expr operand, RenderContext ctx, SourceLocation loc) {
    var val = eval(operand, ctx);
    return switch (op) {
      case NEG -> switch (val) {
        case Value.IntVal(long n) -> Value.of(-n);
        case Value.FloatVal(double n) -> Value.of(-n);
        default -> throw new TemplateException("Cannot negate %s".formatted(val), loc);
      };
      case NOT -> Value.of(!val.isTruthy());
    };
  }

  private Value evalCall(Value fn, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (fn instanceof Value.CallableVal cv) {
      return cv.fn().call(args, kwargs);
    }
    throw new TemplateException("'%s' is not callable".formatted(fn), loc);
  }

  private Value concat(List<Expr> parts, RenderContext ctx) {
    var sb = new StringBuilder();
    for (var part : parts) sb.append(eval(part, ctx).asString());
    return Value.of(sb.toString());
  }

  private Value evalDictLiteral(List<Map.Entry<Expr, Expr>> entries, RenderContext ctx) {
    var map = new LinkedHashMap<String, Value>();
    for (var entry : entries) {
      map.put(eval(entry.getKey(), ctx).asString(), eval(entry.getValue(), ctx));
    }
    return Value.ofMap(map);
  }

  private Value evalSlice(Expr object, Expr start, Expr stop, Expr step, RenderContext ctx, SourceLocation loc) {
    var obj = eval(object, ctx);
    Long startL = start != null ? eval(start, ctx).asLong() : null;
    Long stopL = stop != null ? eval(stop, ctx).asLong() : null;
    Long stepL = step != null ? eval(step, ctx).asLong() : null;
    return ValueAccessor.slice(obj, startL, stopL, stepL, loc);
  }

  // ---- Argument evaluation helpers ----

  public List<Value> evalArgs(List<Expr> args, RenderContext ctx) {
    var out = new ArrayList<Value>(args.size());
    for (var a : args) out.add(eval(a, ctx));
    return out;
  }

  public Map<String, Value> evalKwargs(Map<String, Expr> kwargs, RenderContext ctx) {
    var out = new LinkedHashMap<String, Value>();
    for (var e : kwargs.entrySet()) out.put(e.getKey(), eval(e.getValue(), ctx));
    return out;
  }
}
