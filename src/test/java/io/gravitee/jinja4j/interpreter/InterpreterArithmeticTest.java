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
package io.gravitee.jinja4j.interpreter;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.TemplateException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Arithmetic, equality and comparison operators — covering int/float/bool/string/list mixes.
 */
class InterpreterArithmeticTest {

  private final Environment env = new Environment();

  @Nested
  class Arithmetic {

    @Test
    void floorDivisionWithMixedIntAndFloat() {
      assertThat(env.fromString("{{ 5 // 2.0 }}").render()).isEqualTo("2");
      assertThat(env.fromString("{{ 5.0 // 2 }}").render()).isEqualTo("2");
      assertThat(env.fromString("{{ 7 // 2 }}").render()).isEqualTo("3");
      assertThat(env.fromString("{{ 7.5 // 2.0 }}").render()).isEqualTo("3");
    }

    @Test
    void moduloWithMixedIntAndFloat() {
      assertThat(env.fromString("{{ 7 % 3 }}").render()).isEqualTo("1");
      assertThat(env.fromString("{{ 5 % 2.0 }}").render()).isEqualTo("1.0");
      assertThat(env.fromString("{{ 5.0 % 2 }}").render()).isEqualTo("1.0");
      assertThat(env.fromString("{{ 7.5 % 2.0 }}").render()).isEqualTo("1.5");
    }

    @Test
    void powerSupportsIntFloatAndNegativeExponents() {
      assertThat(env.fromString("{{ 2 ** 3 }}").render()).isEqualTo("8");
      assertThat(env.fromString("{{ 2.0 ** 3 }}").render()).isEqualTo("8.0");
      assertThat(env.fromString("{{ 2 ** 3.0 }}").render()).isEqualTo("8.0");
      assertThat(env.fromString("{{ 2 ** (-1) }}").render()).isEqualTo("0.5");
    }

    @Test
    void addAndSubtractMixedIntAndFloat() {
      assertThat(env.fromString("{{ 1 - 2.0 }}").render()).isEqualTo("-1.0");
      assertThat(env.fromString("{{ 1.5 + 1 }}").render()).isEqualTo("2.5");
      assertThat(env.fromString("{{ 5 + 1.5 }}").render()).isEqualTo("6.5");
    }

    @Test
    void numericMultiplication() {
      assertThat(env.fromString("{{ 3 * 4 }}").render()).isEqualTo("12");
      assertThat(env.fromString("{{ 2.5 * 3 }}").render()).isEqualTo("7.5");
    }

    @Test
    void stringRepetition() {
      assertThat(env.fromString("{{ 'ha' * 3 }}").render()).isEqualTo("hahaha");
      assertThat(env.fromString("{{ 3 * 'ha' }}").render()).isEqualTo("hahaha");
    }

    @Test
    void listConcatenation() {
      assertThat(
        env.fromString("{{ (a + b) | join(', ') }}").render(Map.of("a", java.util.List.of("x"), "b", java.util.List.of("y")))
      ).isEqualTo("x, y");
    }
  }

  @Nested
  class Equality {

    @Test
    void undefinedEqualsUndefined() {
      assertThat(env.fromString("{{ x == y }}").render()).isEqualTo("True");
    }

    @Test
    void nullEqualsNull() {
      var ctx = new HashMap<String, Object>();
      ctx.put("x", null);
      ctx.put("y", null);
      assertThat(env.fromString("{{ x == y }}").render(ctx)).isEqualTo("True");
    }

    @Test
    void nullIsNotEqualToUndefined() {
      var ctx = new HashMap<String, Object>();
      ctx.put("n", null);
      assertThat(env.fromString("{{ n == u }}").render(ctx)).isEqualTo("False");
      assertThat(env.fromString("{{ u == n }}").render(ctx)).isEqualTo("False");
    }

    @Test
    void booleanEquality() {
      assertThat(env.fromString("{{ true == true }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ true == false }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ false == false }}").render()).isEqualTo("True");
    }

    @Test
    void integerEquality() {
      assertThat(env.fromString("{{ 5 == 5 }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 5 == 6 }}").render()).isEqualTo("False");
    }

    @Test
    void floatEqualityBothSides() {
      assertThat(env.fromString("{{ 1.0 == 1.0 }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 1.0 == 2.0 }}").render()).isEqualTo("False");
    }

    @Test
    void intAndFloatCompareEqualWhenNumericallyEqual() {
      assertThat(env.fromString("{{ 1 == 1.0 }}").render()).isEqualTo("True");
    }

    @Test
    void safeStringEqualsStringWhenContentMatches() {
      env.setAutoEscaping(false);
      assertThat(env.fromString("{{ ('hello' | safe) == 'hello' }}").render()).isEqualTo("True");
    }

    @Test
    void safeStringAndStringDifferingContent() {
      assertThat(env.fromString("{{ ('hello' | safe) == 'world' }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 'hello' == ('world' | safe) }}").render()).isEqualTo("False");
    }

    @Test
    void numberVsNonNumberAreNotEqual() {
      assertThat(env.fromString("{{ 5 == 'hello' }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 'hello' == 5 }}").render()).isEqualTo("False");
    }

    @Test
    void listEqualityElementwise() {
      assertThat(env.fromString("{{ [1, 2] == [1, 2] }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ [1, 2] == [1, 3] }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ [1] == [1, 2] }}").render()).isEqualTo("False");
    }

    @Test
    void mapEqualityKeyValueWise() {
      assertThat(env.fromString("{% set a = {'x': 1} %}{% set b = {'x': 1} %}{{ a == b }}").render()).isEqualTo("True");
      assertThat(env.fromString("{% set a = {'x': 1} %}{% set b = {'x': 2} %}{{ a == b }}").render()).isEqualTo("False");
      assertThat(env.fromString("{% set a = {'x': 1} %}{% set b = {'x': 1, 'y': 2} %}{{ a == b }}").render()).isEqualTo(
        "False"
      );
      assertThat(
        env.fromString("{% set a = {'x': 1, 'y': 2} %}{% set b = {'x': 1, 'z': 2} %}{{ a == b }}").render()
      ).isEqualTo("False");
    }

    @Test
    void incompatibleTypesReturnFalse() {
      assertThat(env.fromString("{{ 'hello' == [1, 2] }}").render()).isEqualTo("False");
    }
  }

  @Nested
  class Comparisons {

    @Test
    void stringComparison() {
      assertThat(env.fromString("{{ 'a' < 'b' }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 'b' > 'a' }}").render()).isEqualTo("True");
    }

    @Test
    void lessAndGreaterOrEqual() {
      assertThat(env.fromString("{{ 1 <= 2 }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 2 <= 1 }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 2 >= 1 }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 1 >= 2 }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 5 <= 3 }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 3 >= 5 }}").render()).isEqualTo("False");
    }

    @Test
    void numberComparedToNonNumberFallsBackToString() {
      assertThat(env.fromString("{{ '5' > 'hello' }}").render()).isEqualTo("False");
    }

    @Test
    void incompatibleComparisonThrows() {
      assertThatThrownBy(() -> env.fromString("{{ [1] < 'a' }}").render()).isInstanceOf(TemplateException.class);
    }
  }

  @Nested
  class Errors {

    @Test
    void negationOnNonNumber() {
      assertThatThrownBy(() -> env.fromString("{{ -x }}").render(Map.of("x", "text"))).isInstanceOf(TemplateException.class);
      assertThatThrownBy(() -> env.fromString("{{ -'abc' }}").render()).isInstanceOf(TemplateException.class);
    }

    @Test
    void arithmeticOnNonNumbers() {
      assertThatThrownBy(() -> env.fromString("{{ x - y }}").render(Map.of("x", "a", "y", "b"))).isInstanceOf(
        TemplateException.class
      );
    }
  }
}
