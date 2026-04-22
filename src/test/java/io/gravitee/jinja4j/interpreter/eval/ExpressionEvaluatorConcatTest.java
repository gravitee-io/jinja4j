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

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.RenderContext;
import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.ast.Node.Expr;
import io.gravitee.jinja4j.ast.Node.Expr.Concat;
import io.gravitee.jinja4j.ast.Node.Expr.Literal;
import io.gravitee.jinja4j.ast.Node.Expr.Name;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link Concat} AST node branch in {@link ExpressionEvaluator}.
 *
 * <p>{@code Concat} is an n-ary string-concatenation node that the parser
 * does not currently emit (chains of {@code ~} are produced as a left-leaning
 * {@code BinOp} tree with {@code BinOperator.CONCAT}), but the evaluator
 * branch exists to support it if the parser ever folds those chains. These
 * tests exercise the branch directly by hand-building AST nodes.</p>
 */
class ExpressionEvaluatorConcatTest {

  private static final SourceLocation LOC = SourceLocation.UNKNOWN;

  private final ExpressionEvaluator evaluator = new ExpressionEvaluator(new Environment());
  private final RenderContext ctx = new RenderContext();

  // ---- Helpers ----

  private static Literal lit(Object raw) {
    return new Literal(valueOf(raw), LOC);
  }

  private static Value valueOf(Object raw) {
    return switch (raw) {
      case null -> Value.NULL;
      case String s -> Value.of(s);
      case Integer i -> Value.of((long) i);
      case Long l -> Value.of(l);
      case Double d -> Value.of(d);
      case Boolean b -> Value.of(b);
      case Value v -> v;
      default -> throw new IllegalArgumentException("Unsupported literal: " + raw);
    };
  }

  private static Concat concat(Expr... parts) {
    return new Concat(List.of(parts), LOC);
  }

  private String evalAsString(Expr expr) {
    return evaluator.eval(expr, ctx).asString();
  }

  // ---- Tests ----

  @Test
  @DisplayName("Two string literals are joined")
  void twoStrings() {
    assertThat(evalAsString(concat(lit("hello "), lit("world")))).isEqualTo("hello world");
  }

  @Test
  @DisplayName("N-ary concatenation preserves part order")
  void nAryPreservesOrder() {
    assertThat(evalAsString(concat(lit("a"), lit("b"), lit("c"), lit("d"), lit("e")))).isEqualTo("abcde");
  }

  @Test
  @DisplayName("Single-part Concat yields that part's string representation")
  void singlePart() {
    assertThat(evalAsString(concat(lit("solo")))).isEqualTo("solo");
  }

  @Test
  @DisplayName("Empty parts list yields the empty string")
  void emptyParts() {
    assertThat(evalAsString(new Concat(List.of(), LOC))).isEmpty();
  }

  @Test
  @DisplayName("Non-string parts are stringified (int, float, bool, null)")
  void stringifiesMixedTypes() {
    // Matches Value.asString() semantics: long -> "42", double -> "3.14",
    // bool -> Python-style "True"/"False", null -> "" (renders as empty).
    assertThat(
      evalAsString(concat(lit("x="), lit(42), lit(","), lit(3.14), lit(","), lit(true), lit(","), lit(null)))
    ).isEqualTo("x=42,3.14,True,");
  }

  @Test
  @DisplayName("Name parts resolve against the render context")
  void resolvesNamesFromContext() {
    ctx.set("greeting", Value.of("Hi"));
    ctx.set("name", Value.of("Remi"));
    var expr = concat(new Name("greeting", LOC), lit(", "), new Name("name", LOC), lit("!"));
    assertThat(evalAsString(expr)).isEqualTo("Hi, Remi!");
  }

  @Test
  @DisplayName("Undefined names render as empty (non-strict mode)")
  void undefinedNameRendersEmpty() {
    var expr = concat(lit("["), new Name("missing", LOC), lit("]"));
    assertThat(evalAsString(expr)).isEqualTo("[]");
  }

  @Test
  @DisplayName("Nested Concat is evaluated recursively")
  void nestedConcat() {
    var inner = concat(lit("B"), lit("C"));
    var outer = concat(lit("A"), inner, lit("D"));
    assertThat(evalAsString(outer)).isEqualTo("ABCD");
  }

  @Test
  @DisplayName("Unicode and whitespace characters are preserved verbatim")
  void preservesUnicodeAndWhitespace() {
    assertThat(evalAsString(concat(lit("caf\u00e9 "), lit("\u2603 "), lit("\n\t")))).isEqualTo("café \u2603 \n\t");
  }
}
