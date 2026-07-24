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
package io.gravitee.jinja4j.ast;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.value.Value;
import java.util.*;

/**
 * AST nodes produced by the Jinja2 parser. Every node carries a {@link SourceLocation}
 * for error reporting.
 */
public sealed interface Node {
  SourceLocation location();

  // ---- Statement nodes ----

  /** A sequence of nodes (the top-level template body). */
  record Template(List<Node> body, SourceLocation location) implements Node {}

  /** Raw text output. */
  record Text(String value, SourceLocation location) implements Node {}

  /** {{ expression }} output. */
  record Output(Expr expression, SourceLocation location) implements Node {}

  /** {% if ... %} ... {% elif ... %} ... {% else %} ... {% endif %} */
  record IfNode(List<ConditionBranch> branches, List<Node> elseBranch, SourceLocation location) implements Node {}

  record ConditionBranch(Expr condition, List<Node> body) {}

  /** {% for target in iterable if filter %} ... {% else %} ... {% endfor %} */
  record ForNode(
    String target,
    List<String> unpackTargets,
    Expr iterable,
    Expr filter,
    List<Node> body,
    List<Node> elseBranch,
    boolean recursive,
    SourceLocation location
  ) implements Node {}

  /** {% set name = value %} */
  record SetNode(String target, Expr value, SourceLocation location) implements Node {}

  /** {% set a, b = value %} — tuple-unpacking assignment */
  /** {% set name %} ... {% endset %} — block assignment: body renders to a string. */
  record SetBlockNode(String target, List<Node> body, SourceLocation location) implements Node {}

  record SetUnpackNode(List<String> targets, Expr value, SourceLocation location) implements Node {}

  /** {% set ns.attr = value %} — namespace attribute assignment */
  record SetAttrNode(String namespace, String attr, Expr value, SourceLocation location) implements Node {}

  /** {% do expression %} — evaluate an expression for its side effects, emit nothing */
  record DoNode(Expr expression, SourceLocation location) implements Node {}

  /** {% autoescape flag %} ... {% endautoescape %} — scoped auto-escaping override */
  record AutoescapeNode(Expr flag, List<Node> body, SourceLocation location) implements Node {}

  /** {% import "tpl" as ns %} — import a template's exported names as a namespace */
  record ImportNode(Expr templateExpr, String target, SourceLocation location) implements Node {}

  /** {% from "tpl" import a, b as c %} — import selected names from a template */
  record FromImportNode(Expr templateExpr, List<ImportName> names, SourceLocation location) implements Node {}

  /** A single {@code name} or {@code name as alias} entry in a {% from ... import %}. */
  record ImportName(String name, String alias) {}

  /** {% block name [required] %} ... {% endblock %} */
  record BlockNode(String name, List<Node> body, boolean required, SourceLocation location) implements Node {}

  /** {% extends "base.html" %} */
  record ExtendsNode(Expr templateExpr, SourceLocation location) implements Node {}

  /** {% include "other.html" %} */
  record IncludeNode(Expr templateExpr, boolean ignoreMissing, SourceLocation location) implements Node {}

  /** {% macro name(args) %} ... {% endmacro %} */
  record MacroNode(String name, List<MacroArg> args, List<Node> body, SourceLocation location) implements Node {}

  record MacroArg(String name, Expr defaultValue) {}

  /** {% with %} ... {% endwith %} — scoped assignments */
  record WithNode(List<SetNode> assignments, List<Node> body, SourceLocation location) implements Node {}

  /** {% generation %} — marker tag, treated as no-op */
  record GenerationNode(SourceLocation location) implements Node {}

  /** {% filter name %} ... {% endfilter %} */
  record FilterBlockNode(String filterName, List<Expr> args, List<Node> body, SourceLocation location) implements Node {}

  /**
   * {% call expr %} ... {% endcall %}
   *
   * <p>Invokes {@code expr} (typically a macro call) with a {@code caller}
   * variable bound to a zero-arg callable that renders the body in the
   * surrounding scope.</p>
   */
  record CallBlockNode(Expr call, List<Node> body, SourceLocation location) implements Node {}

  /** {% raw %} ... {% endraw %} */
  record RawNode(String content, SourceLocation location) implements Node {}

  // ---- Expression nodes ----

  sealed interface Expr extends Node {
    record Literal(Value value, SourceLocation location) implements Expr {}

    record Name(String name, SourceLocation location) implements Expr {}

    /** attribute access: obj.attr */
    record GetAttr(Expr object, String attribute, SourceLocation location) implements Expr {}

    /** subscript access: obj[key] */
    record GetItem(Expr object, Expr key, SourceLocation location) implements Expr {}

    /** Binary operation */
    record BinOp(Expr left, BinOperator op, Expr right, SourceLocation location) implements Expr {}

    /**
     * Chained comparison: {@code a < b < c}. {@code operands} has one more
     * entry than {@code operators}; each operand is evaluated exactly once and
     * adjacent pairs are compared left-to-right with short-circuiting.
     */
    record Compare(List<Expr> operands, List<BinOperator> operators, SourceLocation location) implements Expr {}

    /** Unary operation */
    record UnaryOp(UnaryOperator op, Expr operand, SourceLocation location) implements Expr {}

    /** Ternary: trueExpr if condition else falseExpr */
    record Ternary(Expr trueExpr, Expr condition, Expr falseExpr, SourceLocation location) implements Expr {}

    /** Filter application: expr | filter(args) */
    record Filter(Expr value, String name, List<Expr> args, Map<String, Expr> kwargs, SourceLocation location) implements
      Expr {}

    /** Test: expr is test(args) */
    record Test(Expr value, String name, List<Expr> args, boolean negated, SourceLocation location) implements Expr {}

    /** Function/macro call */
    record Call(Expr callee, List<Expr> args, Map<String, Expr> kwargs, SourceLocation location) implements Expr {}

    /** String concatenation with ~ */
    record Concat(List<Expr> parts, SourceLocation location) implements Expr {}

    /** List literal: [a, b, c] */
    record ListLiteral(List<Expr> items, SourceLocation location) implements Expr {}

    /** Dict literal: {a: b, c: d} */
    record DictLiteral(List<Map.Entry<Expr, Expr>> entries, SourceLocation location) implements Expr {}

    /** Tuple literal: (a, b, c) */
    record TupleLiteral(List<Expr> items, SourceLocation location) implements Expr {}

    /** Slice: list[start:stop:step] */
    record Slice(Expr object, Expr start, Expr stop, Expr step, SourceLocation location) implements Expr {}
  }

  enum BinOperator {
    ADD,
    SUB,
    MUL,
    DIV,
    FLOOR_DIV,
    MOD,
    POW,
    EQ,
    NE,
    LT,
    GT,
    LE,
    GE,
    AND,
    OR,
    IN,
    NOT_IN,
    CONCAT,
  }

  enum UnaryOperator {
    NEG,
    NOT,
  }
}
