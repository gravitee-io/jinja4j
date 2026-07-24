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

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Capabilities brought over from minijinja: chained comparisons, dotted-integer
 * lookup, {@code set} tuple unpacking, the {@code do} / {@code autoescape} /
 * {@code import} / {@code from import} tags, required blocks, the {@code format}
 * filter, {@code indent} kwargs, multi-key {@code sort}, the {@code escaped}
 * test, syntax configuration, and lossless numeric comparison.
 */
class MinijinjaParityTest {

  private final Environment env = new Environment();

  // ---- Expressions ----

  @Nested
  class ChainedComparisons {

    @Test
    void ascendingChainHolds() {
      assertThat(env.fromString("{{ 1 < 2 < 3 }}").render()).isEqualTo("True");
    }

    @Test
    void ascendingChainBreaks() {
      assertThat(env.fromString("{{ 1 < 3 < 2 }}").render()).isEqualTo("False");
    }

    @Test
    void rangeCheck() {
      assertThat(env.fromString("{{ 0 <= x <= 10 }}").render(Map.of("x", 5))).isEqualTo("True");
      assertThat(env.fromString("{{ 0 <= x <= 10 }}").render(Map.of("x", 11))).isEqualTo("False");
    }

    @Test
    void middleOperandEvaluatedOnce() {
      // counter() increments each call; a chained comparison must call it once.
      var counter = new int[] { 0 };
      env.addFunction("mid", (args, kwargs) -> io.gravitee.jinja4j.value.Value.of(++counter[0]));
      env.fromString("{{ 0 < mid() < 5 }}").render();
      assertThat(counter[0]).isEqualTo(1);
    }
  }

  @Nested
  class DottedIntegerLookup {

    @Test
    void indexesSequence() {
      assertThat(env.fromString("{{ items.0 }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("a");
    }

    @Test
    void midChain() {
      assertThat(env.fromString("{{ rows.0.1 }}").render(Map.of("rows", List.of(List.of("a", "b"))))).isEqualTo("b");
    }
  }

  @Nested
  class SetUnpacking {

    @Test
    void unpacksTuple() {
      assertThat(env.fromString("{% set a, b = pair %}{{ a }}-{{ b }}").render(Map.of("pair", List.of(1, 2)))).isEqualTo(
        "1-2"
      );
    }
  }

  // ---- Tags ----

  @Nested
  class DoTag {

    @Test
    void evaluatesExpressionWithoutOutput() {
      var calls = new int[] { 0 };
      env.addFunction("record", (args, kwargs) -> {
        calls[0]++;
        return io.gravitee.jinja4j.value.Value.UNDEFINED;
      });
      assertThat(env.fromString("a{% do record() %}b").render()).isEqualTo("ab");
      assertThat(calls[0]).isEqualTo(1);
    }
  }

  @Nested
  class Autoescape {

    @Test
    void enablesEscapingForBlock() {
      var out = env.fromString("{% autoescape true %}{{ v }}{% endautoescape %}").render(Map.of("v", "<b>"));
      assertThat(out).isEqualTo("&lt;b&gt;");
    }

    @Test
    void disablesEscapingForBlock() {
      env.setAutoEscaping(true);
      var out = env.fromString("{% autoescape false %}{{ v }}{% endautoescape %}").render(Map.of("v", "<b>"));
      assertThat(out).isEqualTo("<b>");
    }
  }

  @Nested
  class ImportTags {

    @Test
    void importAsNamespace() {
      env.addTemplate("macros.j2", "{% macro greet(n) %}Hi {{ n }}{% endmacro %}");
      var out = env.fromString("{% import \"macros.j2\" as m %}{{ m.greet(\"Sam\") }}").render();
      assertThat(out).isEqualTo("Hi Sam");
    }

    @Test
    void fromImportWithAlias() {
      env.addTemplate("macros2.j2", "{% macro greet(n) %}Hi {{ n }}{% endmacro %}");
      var out = env.fromString("{% from \"macros2.j2\" import greet as g %}{{ g(\"Sam\") }}").render();
      assertThat(out).isEqualTo("Hi Sam");
    }
  }

  @Nested
  class RequiredBlocks {

    @Test
    void unoverriddenRequiredBlockFails() {
      env.addTemplate("base.j2", "{% block body required %}{% endblock %}");
      assertThatThrownBy(() -> env.fromString("{% extends \"base.j2\" %}").render())
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Required block");
    }

    @Test
    void overriddenRequiredBlockRenders() {
      env.addTemplate("base2.j2", "{% block body required %}{% endblock %}");
      var out = env.fromString("{% extends \"base2.j2\" %}{% block body %}hello{% endblock %}").render();
      assertThat(out).isEqualTo("hello");
    }
  }

  // ---- Filters & tests ----

  @Nested
  class Filters {

    @Test
    void formatFilter() {
      assertThat(env.fromString("{{ \"%s has %d\" | format(name, n) }}").render(Map.of("name", "box", "n", 3))).isEqualTo(
        "box has 3"
      );
    }

    @Test
    void indentWithKwargs() {
      var out = env.fromString("{{ text | indent(width=2, first=true) }}").render(Map.of("text", "a\nb"));
      assertThat(out).isEqualTo("  a\n  b");
    }

    @Test
    void sortMultiKey() {
      var people = List.of(
        Map.of("last", "Smith", "first", "Bob"),
        Map.of("last", "Smith", "first", "Ann"),
        Map.of("last", "Jones", "first", "Zoe")
      );
      var out = env
        .fromString("{% for p in people | sort(attribute=\"last,first\") %}{{ p.first }} {% endfor %}")
        .render(Map.of("people", people));
      assertThat(out).isEqualTo("Zoe Ann Bob ");
    }

    @Test
    void escapedTest() {
      assertThat(env.fromString("{{ (v | safe) is escaped }}").render(Map.of("v", "x"))).isEqualTo("True");
      assertThat(env.fromString("{{ v is escaped }}").render(Map.of("v", "x"))).isEqualTo("False");
    }
  }

  // ---- Syntax config ----

  @Nested
  class SyntaxConfiguration {

    @Test
    void trimBlocksDropsNewlineAfterTag() {
      env.setTrimBlocks(true);
      assertThat(env.fromString("{% if true %}\nhello{% endif %}").render()).isEqualTo("hello");
    }

    @Test
    void lstripBlocksStripsIndentation() {
      env.setLstripBlocks(true);
      assertThat(env.fromString("   {% if true %}x{% endif %}").render()).isEqualTo("x");
    }

    @Test
    void keepTrailingNewlineFalseStripsFinalNewline() {
      env.setKeepTrailingNewline(false);
      assertThat(env.fromString("hello\n").render()).isEqualTo("hello");
    }

    @Test
    void customDelimiters() {
      env.setDelimiters("<%", "%>", "<<", ">>", "<#", "#>");
      assertThat(env.fromString("<< name >>").render(Map.of("name", "Sam"))).isEqualTo("Sam");
    }
  }

  // ---- Numeric correctness ----

  @Nested
  class NumericComparison {

    @Test
    void losslessIntFloatComparison() {
      // 2^53 + 1 has no exact double; must still compare greater than its float form.
      long big = (1L << 53) + 1;
      assertThat(env.fromString("{{ a > b }}").render(Map.of("a", big, "b", (double) big))).isEqualTo("True");
    }
  }
}
