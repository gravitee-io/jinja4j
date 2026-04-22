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
import io.gravitee.jinja4j.interpreter.access.ValueAccessor;
import io.gravitee.jinja4j.interpreter.eval.ExpressionEvaluator;
import io.gravitee.jinja4j.interpreter.output.OutputWriter;
import io.gravitee.jinja4j.value.Namespace;
import io.gravitee.jinja4j.value.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the statement-level AST, producing output into a
 * {@link StringBuilder}.
 *
 * <p>Each statement node family is handled by a small dedicated method,
 * keeping {@link #executeNode} as a flat dispatch table. Expression
 * evaluation is delegated to {@link ExpressionEvaluator}; output
 * formatting to {@link OutputWriter}; attribute/iteration/unpacking
 * to {@link ValueAccessor}.</p>
 */
public final class Interpreter {

  private final Environment env;
  private final ExpressionEvaluator evaluator;
  private final OutputWriter writer;

  public Interpreter(Environment env) {
    this.env = env;
    this.evaluator = new ExpressionEvaluator(env);
    this.writer = new OutputWriter(env);
  }

  /**
   * Render a template AST. If the template extends a parent, the child's
   * blocks are collected first and only the extends node is executed;
   * otherwise the body is executed in order.
   */
  public String render(Node.Template template, RenderContext ctx) {
    var sb = new StringBuilder();
    if (hasExtends(template)) {
      executeExtendingTemplate(template, ctx, sb);
    } else {
      executeBody(template.body(), ctx, sb);
    }
    return sb.toString();
  }

  private static boolean hasExtends(Node.Template template) {
    return template
      .body()
      .stream()
      .anyMatch(n -> n instanceof ExtendsNode);
  }

  private void executeExtendingTemplate(Node.Template template, RenderContext ctx, StringBuilder sb) {
    for (var node : template.body()) {
      if (node instanceof BlockNode(var name, var body, _)) {
        ctx.setBlock(name, body);
      } else if (node instanceof ExtendsNode) {
        executeNode(node, ctx, sb);
      }
      // Text/Output nodes in a child with extends are deliberately skipped.
    }
  }

  private void executeBody(List<Node> body, RenderContext ctx, StringBuilder sb) {
    for (var node : body) {
      executeNode(node, ctx, sb);
    }
  }

  // ---- Dispatch ----

  private void executeNode(Node node, RenderContext ctx, StringBuilder sb) {
    switch (node) {
      case Text(var value, _) -> sb.append(value);
      case RawNode(var content, _) -> sb.append(content);
      case Output(var expr, var loc) -> writer.append(sb, evaluator.eval(expr, ctx), loc);
      case IfNode(var branches, var elseBranch, _) -> executeIf(branches, elseBranch, ctx, sb);
      case ForNode forNode -> executeFor(forNode, ctx, sb);
      case SetNode(var target, var valueExpr, _) -> ctx.set(target, evaluator.eval(valueExpr, ctx));
      case SetAttrNode(var nsName, var attr, var valueExpr, var loc) -> executeSetAttr(nsName, attr, valueExpr, ctx, loc);
      case BlockNode(var name, var body, _) -> executeBlock(name, body, ctx, sb);
      case ExtendsNode(var templateExpr, var loc) -> executeExtends(templateExpr, ctx, sb, loc);
      case IncludeNode(var templateExpr, var ignoreMissing, var loc) -> executeInclude(
        templateExpr,
        ignoreMissing,
        ctx,
        sb,
        loc
      );
      case MacroNode(var name, var args, var body, var loc) -> registerMacro(name, args, body, ctx);
      case WithNode(var assignments, var body, _) -> executeWith(assignments, body, ctx, sb);
      case FilterBlockNode(var filterName, var args, var body, var loc) -> executeFilterBlock(
        filterName,
        args,
        body,
        ctx,
        sb,
        loc
      );
      case Node.Template(var body, _) -> executeBody(body, ctx, sb);
      case GenerationNode(_) -> {
        // No-op marker
      }
      default -> {
        // Expression nodes shouldn't appear at statement level
      }
    }
  }

  // ---- Control flow ----

  private void executeIf(List<ConditionBranch> branches, List<Node> elseBranch, RenderContext ctx, StringBuilder sb) {
    for (var branch : branches) {
      if (evaluator.eval(branch.condition(), ctx).isTruthy()) {
        executeBody(branch.body(), ctx, sb);
        return;
      }
    }
    executeBody(elseBranch, ctx, sb);
  }

  private void executeFor(ForNode forNode, RenderContext ctx, StringBuilder sb) {
    var items = ValueAccessor.toIterable(evaluator.eval(forNode.iterable(), ctx), forNode.location());
    if (items.isEmpty()) {
      executeBody(forNode.elseBranch(), ctx, sb);
      return;
    }
    int length = items.size();
    for (int i = 0; i < length; i++) {
      var scope = ctx.pushScope();
      bindLoopTarget(forNode, items.get(i), scope);
      scope.set("loop", buildLoopVariable(items, i, length));
      executeBody(forNode.body(), scope, sb);
      ctx.popScope();
    }
  }

  private void bindLoopTarget(ForNode forNode, Value item, RenderContext scope) {
    if (forNode.target() != null) {
      scope.set(forNode.target(), item);
    } else if (forNode.unpackTargets() != null) {
      ValueAccessor.unpackInto(item, forNode.unpackTargets(), scope, forNode.location());
    }
  }

  private static Value buildLoopVariable(List<Value> items, int i, int length) {
    var loopMap = new LinkedHashMap<String, Value>();
    loopMap.put("index", Value.of(i + 1));
    loopMap.put("index0", Value.of(i));
    loopMap.put("first", Value.of(i == 0));
    loopMap.put("last", Value.of(i == length - 1));
    loopMap.put("length", Value.of(length));
    loopMap.put("revindex", Value.of(length - i));
    loopMap.put("revindex0", Value.of(length - i - 1));
    loopMap.put("previtem", i > 0 ? items.get(i - 1) : Value.UNDEFINED);
    loopMap.put("nextitem", i < length - 1 ? items.get(i + 1) : Value.UNDEFINED);
    int finalI = i;
    loopMap.put(
      "cycle",
      Value.ofCallable("loop.cycle", (args, kwargs) -> args.isEmpty() ? Value.UNDEFINED : args.get(finalI % args.size()))
    );
    return Value.ofMap(loopMap);
  }

  // ---- Assignment ----

  private void executeSetAttr(String nsName, String attr, Expr valueExpr, RenderContext ctx, SourceLocation loc) {
    if (ctx.resolve(nsName) instanceof Value.NamespaceVal(Namespace ns)) {
      ns.set(attr, evaluator.eval(valueExpr, ctx));
      return;
    }
    throw new TemplateException("Cannot set attribute on non-namespace '%s'".formatted(nsName), loc);
  }

  // ---- Template inheritance ----

  private void executeBlock(String name, List<Node> body, RenderContext ctx, StringBuilder sb) {
    var overridden = ctx.getBlock(name);
    executeBody(overridden != null ? overridden : body, ctx, sb);
  }

  private void executeExtends(Expr templateExpr, RenderContext ctx, StringBuilder sb, SourceLocation loc) {
    var parentName = evaluator.eval(templateExpr, ctx).asString();
    var parentAst = loadTemplateAst(parentName, loc, false);
    // Render the parent with the child's blocks available via ctx.
    sb.append(render(parentAst, ctx));
  }

  private void executeInclude(
    Expr templateExpr,
    boolean ignoreMissing,
    RenderContext ctx,
    StringBuilder sb,
    SourceLocation loc
  ) {
    var includeName = evaluator.eval(templateExpr, ctx).asString();
    var includeAst = loadTemplateAst(includeName, loc, ignoreMissing);
    if (includeAst == null) return; // ignoreMissing
    executeBody(includeAst.body(), ctx, sb);
  }

  private Node.Template loadTemplateAst(String name, SourceLocation loc, boolean ignoreMissing) {
    var source = env.loadTemplate(name);
    if (source == null) {
      if (ignoreMissing) return null;
      throw new TemplateException("Template '%s' not found".formatted(name), loc);
    }
    var tokens = new Lexer(source, name).tokenizeAndTrim();
    return new Parser(tokens, name).parse();
  }

  // ---- Macros ----

  private void registerMacro(String name, List<MacroArg> args, List<Node> body, RenderContext ctx) {
    ctx.set(name, Value.ofCallable(name, (callArgs, callKwargs) -> invokeMacro(args, body, ctx, callArgs, callKwargs)));
  }

  private Value invokeMacro(
    List<MacroArg> args,
    List<Node> body,
    RenderContext ctx,
    List<Value> callArgs,
    Map<String, Value> callKwargs
  ) {
    var macroScope = ctx.pushScope();
    for (int i = 0; i < args.size(); i++) {
      macroScope.set(args.get(i).name(), resolveMacroArg(args.get(i), i, callArgs, callKwargs, macroScope));
    }
    var macroSb = new StringBuilder();
    executeBody(body, macroScope, macroSb);
    ctx.popScope();
    return Value.of(macroSb.toString());
  }

  private Value resolveMacroArg(
    MacroArg arg,
    int position,
    List<Value> callArgs,
    Map<String, Value> callKwargs,
    RenderContext macroScope
  ) {
    if (callKwargs.containsKey(arg.name())) return callKwargs.get(arg.name());
    if (position < callArgs.size()) return callArgs.get(position);
    if (arg.defaultValue() != null) return evaluator.eval(arg.defaultValue(), macroScope);
    return Value.UNDEFINED;
  }

  // ---- With / Filter block ----

  private void executeWith(List<SetNode> assignments, List<Node> body, RenderContext ctx, StringBuilder sb) {
    var scope = ctx.pushScope();
    for (var a : assignments) {
      scope.set(a.target(), evaluator.eval(a.value(), scope));
    }
    executeBody(body, scope, sb);
    ctx.popScope();
  }

  private void executeFilterBlock(
    String filterName,
    List<Expr> args,
    List<Node> body,
    RenderContext ctx,
    StringBuilder sb,
    SourceLocation loc
  ) {
    var innerSb = new StringBuilder();
    executeBody(body, ctx, innerSb);
    var result = env.applyFilter(filterName, Value.of(innerSb.toString()), evaluator.evalArgs(args, ctx), Map.of(), loc);
    sb.append(result.asString());
  }
}
