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
package io.gravitee.jinja4j.test;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.TemplateException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of every built-in Jinja2 test, exercised through the template engine.
 */
class BuiltinTestsTest {

  private final Environment env = new Environment();

  @Nested
  class TypeTests {

    @Test
    void booleanTest() {
      assertThat(env.fromString("{{ x is boolean }}").render(Map.of("x", true))).isEqualTo("True");
      assertThat(env.fromString("{{ x is boolean }}").render(Map.of("x", 42))).isEqualTo("False");
    }

    @Test
    void integerTest() {
      assertThat(env.fromString("{{ x is integer }}").render(Map.of("x", 42))).isEqualTo("True");
      assertThat(env.fromString("{{ x is integer }}").render(Map.of("x", 3.14))).isEqualTo("False");
    }

    @Test
    void floatTest() {
      assertThat(env.fromString("{{ x is float }}").render(Map.of("x", 3.14))).isEqualTo("True");
      assertThat(env.fromString("{{ x is float }}").render(Map.of("x", 42))).isEqualTo("False");
    }

    @Test
    void numberTest() {
      assertThat(env.fromString("{{ x is number }}").render(Map.of("x", 42))).isEqualTo("True");
      assertThat(env.fromString("{{ x is number }}").render(Map.of("x", 3.14))).isEqualTo("True");
      assertThat(env.fromString("{{ x is number }}").render(Map.of("x", "hello"))).isEqualTo("False");
    }

    @Test
    void sequenceTest() {
      assertThat(env.fromString("{{ x is sequence }}").render(Map.of("x", List.of(1)))).isEqualTo("True");
      assertThat(env.fromString("{{ x is sequence }}").render(Map.of("x", "hello"))).isEqualTo("False");
    }

    @Test
    void callableTestOnMacroAndValue() {
      assertThat(env.fromString("{% macro m() %}{% endmacro %}{{ m is callable }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ x is callable }}").render(Map.of("x", 42))).isEqualTo("False");
    }

    @Test
    void iterableMatchesSequenceMappingAndString() {
      assertThat(env.fromString("{{ x is iterable }}").render(Map.of("x", List.of(1)))).isEqualTo("True");
      assertThat(env.fromString("{{ x is iterable }}").render(Map.of("x", Map.of("a", 1)))).isEqualTo("True");
      assertThat(env.fromString("{{ x is iterable }}").render(Map.of("x", "hello"))).isEqualTo("True");
      assertThat(env.fromString("{{ x is iterable }}").render(Map.of("x", 42))).isEqualTo("False");
    }
  }

  @Nested
  class DefinedAndNone {

    @Test
    void definedAndUndefined() {
      var ctx = new java.util.HashMap<String, Object>();
      ctx.put("x", 1);
      assertThat(env.fromString("{{ x is defined }}").render(ctx)).isEqualTo("True");
      assertThat(env.fromString("{{ missing is defined }}").render(ctx)).isEqualTo("False");
      assertThat(env.fromString("{{ x is undefined }}").render(ctx)).isEqualTo("False");
      assertThat(env.fromString("{{ missing is undefined }}").render(ctx)).isEqualTo("True");
    }

    @Test
    void noneMatchesOnlyExplicitNull() {
      var ctx = new java.util.HashMap<String, Object>();
      ctx.put("n", null);
      assertThat(env.fromString("{{ n is none }}").render(ctx)).isEqualTo("True");
      assertThat(env.fromString("{{ x is none }}").render()).isEqualTo("False");
    }
  }

  @Nested
  class Equality {

    @Test
    void sameasReturnsTrueOnlyForIdenticalReferences() {
      // Boolean singletons are same-reference
      assertThat(env.fromString("{{ true is sameas(true) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ false is sameas(false) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ true is sameas(false) }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 'a' is sameas('b') }}").render()).isEqualTo("False");
    }

    @Test
    void sameasWithoutArgReturnsFalse() {
      assertThat(env.fromString("{{ x is sameas }}").render(Map.of("x", 1))).isEqualTo("False");
    }

    @Test
    void eqAndAliases() {
      assertThat(env.fromString("{{ 42 is eq(42) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 42 is eq(43) }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 42 is equalto(42) }}").render()).isEqualTo("True");
    }

    @Test
    void neTest() {
      assertThat(env.fromString("{{ 42 is ne(43) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 42 is ne(42) }}").render()).isEqualTo("False");
    }

    @Test
    void missingArgsFallBackToDefaults() {
      // Documented behaviour of the comparison tests when no argument is supplied:
      // eq/lt/gt/le/ge → false;  ne → true (because "not equal to nothing")
      assertThat(env.fromString("{{ x is eq }}").render(Map.of("x", 1))).isEqualTo("False");
      assertThat(env.fromString("{{ x is ne }}").render(Map.of("x", 1))).isEqualTo("True");
      assertThat(env.fromString("{{ x is lt }}").render(Map.of("x", 1))).isEqualTo("False");
      assertThat(env.fromString("{{ x is le }}").render(Map.of("x", 1))).isEqualTo("False");
      assertThat(env.fromString("{{ x is gt }}").render(Map.of("x", 1))).isEqualTo("False");
      assertThat(env.fromString("{{ x is ge }}").render(Map.of("x", 1))).isEqualTo("False");
    }
  }

  @Nested
  class Comparisons {

    @Test
    void ltAndAlias() {
      assertThat(env.fromString("{{ 1 is lt(2) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 2 is lt(1) }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 1 is lessthan(2) }}").render()).isEqualTo("True");
    }

    @Test
    void gtAndAlias() {
      assertThat(env.fromString("{{ 2 is gt(1) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 1 is gt(2) }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 2 is greaterthan(1) }}").render()).isEqualTo("True");
    }

    @Test
    void le() {
      assertThat(env.fromString("{{ 1 is le(1) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 2 is le(1) }}").render()).isEqualTo("False");
    }

    @Test
    void ge() {
      assertThat(env.fromString("{{ 2 is ge(2) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 1 is ge(2) }}").render()).isEqualTo("False");
    }
  }

  @Nested
  class NumericParity {

    @Test
    void oddMatchesOnlyOddIntegers() {
      assertThat(env.fromString("{{ 3 is odd }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 4 is odd }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 'hello' is odd }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 3.5 is odd }}").render()).isEqualTo("False");
    }

    @Test
    void evenMatchesOnlyEvenIntegers() {
      assertThat(env.fromString("{{ 4 is even }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 3 is even }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 'hello' is even }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 4.0 is even }}").render()).isEqualTo("False");
    }

    @Test
    void divisibleby() {
      assertThat(env.fromString("{{ 6 is divisibleby(3) }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 7 is divisibleby(3) }}").render()).isEqualTo("False");
      // Missing arg, non-integer value, and zero divisor all return false.
      assertThat(env.fromString("{{ 10 is divisibleby }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 10 is divisibleby(0) }}").render()).isEqualTo("False");
    }
  }

  @Nested
  class BooleanLiterals {

    @Test
    void isTrueOnlyForBoolTrue() {
      assertThat(env.fromString("{{ x is true }}").render(Map.of("x", true))).isEqualTo("True");
      assertThat(env.fromString("{{ x is true }}").render(Map.of("x", false))).isEqualTo("False");
      assertThat(env.fromString("{{ x is true }}").render(Map.of("x", 1))).isEqualTo("False");
      assertThat(env.fromString("{{ false is true }}").render()).isEqualTo("False");
    }

    @Test
    void isFalseOnlyForBoolFalse() {
      assertThat(env.fromString("{{ x is false }}").render(Map.of("x", false))).isEqualTo("True");
      assertThat(env.fromString("{{ x is false }}").render(Map.of("x", true))).isEqualTo("False");
      assertThat(env.fromString("{{ x is false }}").render(Map.of("x", 0))).isEqualTo("False");
      assertThat(env.fromString("{{ true is false }}").render()).isEqualTo("False");
    }
  }

  @Nested
  class StringCase {

    @Test
    void lowerAndUpperCheckString() {
      assertThat(env.fromString("{{ 'hello' is lower }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 'Hello' is lower }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 'HELLO' is upper }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ 'Hello' is upper }}").render()).isEqualTo("False");
    }

    @Test
    void nonStringValuesDoNotMatchCaseTests() {
      assertThat(env.fromString("{{ 42 is lower }}").render()).isEqualTo("False");
      assertThat(env.fromString("{{ 42 is upper }}").render()).isEqualTo("False");
    }
  }

  @Nested
  class Custom {

    @Test
    void customTestCanBeRegistered() {
      env.addTest("positive", (v, args, loc) -> v.isNumber() && v.asDouble() > 0);
      assertThat(env.fromString("{{ 5 is positive }}").render()).isEqualTo("True");
      assertThat(env.fromString("{{ (-1) is positive }}").render()).isEqualTo("False");
    }

    @Test
    void unknownTestNameThrows() {
      assertThatThrownBy(() -> env.fromString("{{ x is foobar }}").render(Map.of("x", 1)))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Unknown test");
    }

    @Test
    void facadeExposesHas() {
      assertThat(BuiltinTests.has("defined")).isTrue();
      assertThat(BuiltinTests.has("nonexistent")).isFalse();
    }
  }
}
