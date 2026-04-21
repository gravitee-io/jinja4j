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
package io.gravitee.jinja4j.filter;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.value.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of {@link FilterSupport} — the shared helper for JSON serialization
 * and the test-predicate matrix used by {@code select/reject/selectattr/rejectattr}.
 */
class FilterSupportTest {

  private final Environment env = new Environment();

  // ---------------------------------------------------------------------------
  // JSON serialization (via tojson filter and direct calls)
  // ---------------------------------------------------------------------------

  @Nested
  class ToJson {

    @Test
    void booleansCompactForm() {
      assertThat(FilterSupport.toJson(Value.of(false))).isEqualTo("false");
      assertThat(FilterSupport.toJson(Value.of(true))).isEqualTo("true");
    }

    @Test
    void booleansIndentedForm() {
      assertThat(FilterSupport.toJsonIndented(Value.of(false), 2, 0)).isEqualTo("false");
      assertThat(FilterSupport.toJsonIndented(Value.of(true), 2, 0)).isEqualTo("true");
    }

    @Test
    void tojsonFilterRendersBooleanFalse() {
      assertThat(env.fromString("{{ false | tojson }}").render()).isEqualTo("false");
      assertThat(env.fromString("{{ false | tojson(indent=2) }}").render()).isEqualTo("false");
    }

    @Test
    void multiEntryMapCompact() {
      var data = new LinkedHashMap<String, Object>();
      data.put("a", 1);
      data.put("b", 2);
      data.put("c", 3);
      assertThat(env.fromString("{{ data | tojson }}").render(Map.of("data", data)))
        .contains("\"a\": 1")
        .contains("\"b\": 2")
        .contains("\"c\": 3")
        .contains(", ");
    }

    @Test
    void multiEntryMapIndentedHasCommasAndNewlines() {
      var data = new LinkedHashMap<String, Object>();
      data.put("a", 1);
      data.put("b", 2);
      assertThat(env.fromString("{{ data | tojson(indent=2) }}").render(Map.of("data", data))).contains(",").contains("\n");
    }

    @Test
    void multiItemListIndented() {
      assertThat(env.fromString("{{ data | tojson(indent=2) }}").render(Map.of("data", List.of(1, 2, 3))))
        .contains(",")
        .contains("\n  1")
        .contains("\n  2")
        .contains("\n  3");
    }

    @Test
    void floatValueCompactAndIndented() {
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", 3.14))).isEqualTo("3.14");
      assertThat(env.fromString("{{ x | tojson(indent=2) }}").render(Map.of("x", 3.14))).isEqualTo("3.14");
    }

    @Test
    void safeStringSerialisesLikeString() {
      assertThat(env.fromString("{{ ('hi' | safe) | tojson }}").render()).isEqualTo("\"hi\"");
      assertThat(env.fromString("{{ ('hi' | safe) | tojson(indent=2) }}").render()).isEqualTo("\"hi\"");
    }

    @Test
    void undefinedSerialisesToNull() {
      assertThat(env.fromString("{{ missing | tojson }}").render()).isEqualTo("null");
      assertThat(env.fromString("{{ missing | tojson(indent=2) }}").render()).isEqualTo("null");
    }

    @Test
    void explicitNullSerialisesToNull() {
      var ctx = new java.util.HashMap<String, Object>();
      ctx.put("x", null);
      assertThat(env.fromString("{{ x | tojson }}").render(ctx)).isEqualTo("null");
      assertThat(env.fromString("{{ x | tojson(indent=2) }}").render(ctx)).isEqualTo("null");
    }

    @Test
    void callableSerialisesToNull() {
      assertThat(env.fromString("{% macro m() %}{% endmacro %}{{ m | tojson }}").render()).isEqualTo("null");
      assertThat(env.fromString("{% macro m() %}{% endmacro %}{{ m | tojson(indent=2) }}").render()).isEqualTo("null");
    }

    @Test
    void namespaceSerialisesToNull() {
      assertThat(env.fromString("{% set ns = namespace(x=1) %}{{ ns | tojson }}").render()).isEqualTo("null");
      assertThat(env.fromString("{% set ns = namespace(x=1) %}{{ ns | tojson(indent=2) }}").render()).isEqualTo("null");
    }

    @Test
    void specialCharactersAreEscaped() {
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\\b"))).isEqualTo("\"a\\\\b\"");
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\"b"))).isEqualTo("\"a\\\"b\"");
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\tb"))).isEqualTo("\"a\\tb\"");
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\rb"))).isEqualTo("\"a\\rb\"");
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\nb"))).isEqualTo("\"a\\nb\"");
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\u0001b"))).contains("\\u0001");
    }

    @Test
    void backspaceAndFormFeedUseShortEscapes() {
      // Both characters are valid JSON but must use their canonical short escapes
      // rather than the fallback unicode-escape form.
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\bb"))).isEqualTo("\"a\\bb\"");
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "a\fb"))).isEqualTo("\"a\\fb\"");
    }

    @Test
    void allControlCharactersInOneString() {
      // Combined sanity check: \b, \t, \n, \f, \r all escaped via the short forms
      assertThat(env.fromString("{{ x | tojson }}").render(Map.of("x", "\b\t\n\f\r"))).isEqualTo("\"\\b\\t\\n\\f\\r\"");
    }

    @Test
    void emptyCollectionsIndented() {
      assertThat(env.fromString("{{ x | tojson(indent=2) }}").render(Map.of("x", List.of()))).isEqualTo("[]");
      assertThat(env.fromString("{{ x | tojson(indent=2) }}").render(Map.of("x", Map.of()))).isEqualTo("{}");
    }
  }

  // ---------------------------------------------------------------------------
  // applyTestPredicate matrix (invoked via the `select` filter)
  // ---------------------------------------------------------------------------

  @Nested
  class SelectEquality {

    @Test
    void eqMissingArgRejectsEverything() {
      assertThat(env.fromString("{{ items | select('eq') | length }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo(
        "0"
      );
      assertThat(
        env.fromString("{{ items | select('equalto') | length }}").render(Map.of("items", List.of(1, 2)))
      ).isEqualTo("0");
      assertThat(env.fromString("{{ items | select('sameas') | length }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
        "0"
      );
    }

    @Test
    void neMissingArgKeepsEverything() {
      assertThat(env.fromString("{{ items | select('ne') | length }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo(
        "3"
      );
    }

    @Test
    void eqOnMixedNumericAndStringValues() {
      // valueEquals() treats numeric-and-string equal when values match numerically
      assertThat(
        env.fromString("{{ items | select('eq', 3) | length }}").render(Map.of("items", List.of(3, "3")))
      ).isEqualTo("2");
      assertThat(
        env.fromString("{{ items | select('eq', 'x') | length }}").render(Map.of("items", List.of("x", "y")))
      ).isEqualTo("1");
    }
  }

  @Nested
  class SelectComparisons {

    @Test
    void ltMissingArg() {
      assertThat(env.fromString("{{ items | select('lt') | length }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo(
        "0"
      );
      assertThat(
        env.fromString("{{ items | select('lessthan') | length }}").render(Map.of("items", List.of(1, 2)))
      ).isEqualTo("0");
    }

    @Test
    void leMissingArg() {
      assertThat(env.fromString("{{ items | select('le') | length }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
        "0"
      );
    }

    @Test
    void gtMissingArg() {
      assertThat(env.fromString("{{ items | select('gt') | length }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
        "0"
      );
      assertThat(
        env.fromString("{{ items | select('greaterthan') | length }}").render(Map.of("items", List.of(1)))
      ).isEqualTo("0");
    }

    @Test
    void geMissingArg() {
      assertThat(env.fromString("{{ items | select('ge') | length }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
        "0"
      );
    }
  }

  @Nested
  class SelectContainment {

    @Test
    void inWithMissingArg() {
      assertThat(env.fromString("{{ items | select('in') | length }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
        "0"
      );
    }

    @Test
    void inWithListContainer() {
      assertThat(
        env
          .fromString("{{ items | select('in', allowed) | join(', ') }}")
          .render(Map.of("items", List.of("a", "b", "c"), "allowed", List.of("a", "c")))
      ).isEqualTo("a, c");
    }

    @Test
    void inWithStringContainer() {
      assertThat(
        env.fromString("{{ items | select('in', 'abc') | join(', ') }}").render(Map.of("items", List.of("a", "d", "b")))
      ).isEqualTo("a, b");
    }

    @Test
    void inWithMapContainer() {
      assertThat(
        env
          .fromString("{{ items | select('in', data) | join(', ') }}")
          .render(Map.of("items", List.of("x", "y", "z"), "data", Map.of("x", 1, "z", 3)))
      ).isEqualTo("x, z");
    }

    @Test
    void inWithNonContainerYieldsNothing() {
      assertThat(
        env.fromString("{{ items | select('in', 42) | length }}").render(Map.of("items", List.of("a", "b")))
      ).isEqualTo("0");
    }
  }

  @Nested
  class SelectTypeAndState {

    @Test
    void definedAndUndefinedOpposites() {
      var ctx = new java.util.HashMap<String, Object>();
      ctx.put("items", List.of(1, 2));
      assertThat(env.fromString("{{ items | select('defined') | length }}").render(ctx)).isEqualTo("2");
      assertThat(env.fromString("{{ items | select('undefined') | length }}").render(ctx)).isEqualTo("0");
    }

    @Test
    void trueRejectsBooleanFalse() {
      assertThat(
        env.fromString("{{ items | select('true') | length }}").render(Map.of("items", List.of(false, false)))
      ).isEqualTo("0");
    }

    @Test
    void falseRejectsBooleanTrue() {
      assertThat(
        env.fromString("{{ items | select('false') | length }}").render(Map.of("items", List.of(true, true)))
      ).isEqualTo("0");
    }

    @Test
    void iterableMatchesStrings() {
      assertThat(
        env.fromString("{{ items | select('iterable') | length }}").render(Map.of("items", List.of("hello", 42, "world")))
      ).isEqualTo("2");
    }
  }

  @Nested
  class SelectNumeric {

    @Test
    void oddAndEvenRejectNonIntegers() {
      assertThat(
        env.fromString("{{ items | select('odd') | length }}").render(Map.of("items", List.of("abc", 1.5)))
      ).isEqualTo("0");
      assertThat(
        env.fromString("{{ items | select('even') | length }}").render(Map.of("items", List.of("abc", 1.5)))
      ).isEqualTo("0");
    }

    @Test
    void divisiblebyMissingArg() {
      assertThat(
        env.fromString("{{ items | select('divisibleby') | length }}").render(Map.of("items", List.of(1, 2)))
      ).isEqualTo("0");
    }

    @Test
    void divisiblebyRejectsNonIntegers() {
      assertThat(
        env.fromString("{{ items | select('divisibleby', 2) | length }}").render(Map.of("items", List.of("abc", 1.5)))
      ).isEqualTo("0");
    }

    @Test
    void divisiblebyZeroYieldsNothing() {
      assertThat(
        env.fromString("{{ items | select('divisibleby', 0) | length }}").render(Map.of("items", List.of(6, 9)))
      ).isEqualTo("0");
    }
  }

  @Nested
  class SelectPositiveMatches {

    // Each test covers the "true" branch of one test-name case in applyTestPredicate.

    @Test
    void ne() {
      assertThat(
        env.fromString("{{ items | select('ne', 2) | join(',') }}").render(Map.of("items", List.of(1, 2, 3)))
      ).isEqualTo("1,3");
    }

    @Test
    void lt() {
      assertThat(
        env.fromString("{{ items | select('lt', 3) | join(',') }}").render(Map.of("items", List.of(1, 2, 3, 4)))
      ).isEqualTo("1,2");
      assertThat(
        env.fromString("{{ items | select('lessthan', 3) | join(',') }}").render(Map.of("items", List.of(1, 2, 3)))
      ).isEqualTo("1,2");
    }

    @Test
    void le() {
      assertThat(
        env.fromString("{{ items | select('le', 2) | join(',') }}").render(Map.of("items", List.of(1, 2, 3)))
      ).isEqualTo("1,2");
    }

    @Test
    void gt() {
      assertThat(
        env.fromString("{{ items | select('gt', 2) | join(',') }}").render(Map.of("items", List.of(1, 2, 3, 4)))
      ).isEqualTo("3,4");
      assertThat(
        env.fromString("{{ items | select('greaterthan', 2) | join(',') }}").render(Map.of("items", List.of(1, 3)))
      ).isEqualTo("3");
    }

    @Test
    void ge() {
      assertThat(
        env.fromString("{{ items | select('ge', 3) | join(',') }}").render(Map.of("items", List.of(1, 2, 3, 4)))
      ).isEqualTo("3,4");
    }

    @Test
    void defined() {
      assertThat(
        env.fromString("{{ items | select('defined') | join(',') }}").render(Map.of("items", List.of(1, 2)))
      ).isEqualTo("1,2");
    }

    @Test
    void none() {
      var ctx = new java.util.HashMap<String, Object>();
      var items = new java.util.ArrayList<>();
      items.add(1);
      items.add(null);
      items.add(2);
      ctx.put("items", items);
      assertThat(env.fromString("{{ items | select('none') | length }}").render(ctx)).isEqualTo("1");
    }

    @Test
    void trueOnBoolTrue() {
      assertThat(
        env.fromString("{{ items | select('true') | length }}").render(Map.of("items", List.of(true, false, true)))
      ).isEqualTo("2");
    }

    @Test
    void falseOnBoolFalse() {
      assertThat(
        env.fromString("{{ items | select('false') | length }}").render(Map.of("items", List.of(true, false, false)))
      ).isEqualTo("2");
    }

    @Test
    void stringType() {
      assertThat(
        env.fromString("{{ items | select('string') | length }}").render(Map.of("items", List.of("a", 1, "b")))
      ).isEqualTo("2");
    }

    @Test
    void numberType() {
      assertThat(
        env.fromString("{{ items | select('number') | length }}").render(Map.of("items", List.of("a", 1, 2.0)))
      ).isEqualTo("2");
    }

    @Test
    void integerType() {
      assertThat(
        env.fromString("{{ items | select('integer') | length }}").render(Map.of("items", List.of(1, 2.0, 3)))
      ).isEqualTo("2");
    }

    @Test
    void floatType() {
      assertThat(
        env.fromString("{{ items | select('float') | length }}").render(Map.of("items", List.of(1, 2.0, 3)))
      ).isEqualTo("1");
    }

    @Test
    void mappingType() {
      var items = List.of(Map.of("a", 1), "not-a-map", Map.of("b", 2));
      assertThat(env.fromString("{{ items | select('mapping') | length }}").render(Map.of("items", items))).isEqualTo("2");
    }

    @Test
    void sequenceType() {
      var items = new java.util.ArrayList<>();
      items.add(List.of(1));
      items.add("str");
      items.add(42);
      assertThat(env.fromString("{{ items | select('sequence') | length }}").render(Map.of("items", items))).isEqualTo("2");
    }

    @Test
    void oddOnIntegers() {
      assertThat(
        env.fromString("{{ items | select('odd') | join(',') }}").render(Map.of("items", List.of(1, 2, 3, 4, 5)))
      ).isEqualTo("1,3,5");
    }

    @Test
    void evenOnIntegers() {
      assertThat(
        env.fromString("{{ items | select('even') | join(',') }}").render(Map.of("items", List.of(1, 2, 3, 4)))
      ).isEqualTo("2,4");
    }

    @Test
    void divisibleby() {
      assertThat(
        env.fromString("{{ items | select('divisibleby', 3) | join(',') }}").render(Map.of("items", List.of(1, 3, 6, 7, 9)))
      ).isEqualTo("3,6,9");
    }

    @Test
    void unknownTestNameFallsBackToTruthiness() {
      assertThat(
        env.fromString("{{ items | select('nonexistent_test') | join(',') }}").render(Map.of("items", List.of("a", "", "b")))
      ).isEqualTo("a,b");
    }
  }

  @Nested
  class SelectattrAndRejectattr {

    // These also exercise getAttr() and compareNum().

    @Test
    void selectattrByAttributeTruthiness() {
      var items = List.of(Map.of("name", "A", "active", true), Map.of("name", "B", "active", false));
      assertThat(
        env
          .fromString("{{ items | selectattr('active') | map(attribute='name') | join(',') }}")
          .render(Map.of("items", items))
      ).isEqualTo("A");
    }

    @Test
    void selectattrByComparison() {
      var items = List.of(Map.of("name", "A", "age", 10), Map.of("name", "B", "age", 20), Map.of("name", "C", "age", 30));
      assertThat(
        env
          .fromString("{{ items | selectattr('age', 'ge', 20) | map(attribute='name') | join(',') }}")
          .render(Map.of("items", items))
      ).isEqualTo("B,C");
    }

    @Test
    void selectattrOnNonMapItemReturnsEmpty() {
      assertThat(
        env.fromString("{{ items | selectattr('x') | join(',') }}").render(Map.of("items", List.of("not_a_map")))
      ).isEqualTo("");
    }
  }
}
