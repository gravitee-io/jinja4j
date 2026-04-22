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
 * Global functions exposed by {@link Environment}: {@code range}, {@code joiner},
 * {@code cycler}, {@code lipsum}, {@code strftime_now}, {@code raise_exception}.
 */
class EnvironmentGlobalsTest {

  private final Environment env = new Environment();

  @Nested
  class Range {

    @Test
    void positiveStep() {
      assertThat(env.fromString("{% for i in range(0, 10, 3) %}{{ i }} {% endfor %}").render()).isEqualTo("0 3 6 9 ");
    }

    @Test
    void negativeStep() {
      assertThat(env.fromString("{% for i in range(5, 0, -1) %}{{ i }} {% endfor %}").render()).isEqualTo("5 4 3 2 1 ");
      assertThat(env.fromString("{% for i in range(3, 0, -1) %}{{ i }}{% endfor %}").render()).isEqualTo("321");
    }

    @Test
    void noArgumentsYieldsEmptyRange() {
      assertThat(env.fromString("{% for i in range() %}X{% else %}empty{% endfor %}").render()).isEqualTo("empty");
    }
  }

  @Nested
  class Joiner {

    @Test
    void producesSeparatorBetweenIterations() {
      var tmpl = env.fromString("{% set j = joiner(', ') %}{% for x in items %}{{ j() }}{{ x }}{% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("a, b, c");
    }

    @Test
    void usesCommaWhenNoSeparatorIsSupplied() {
      var tmpl = env.fromString("{% set j = joiner() %}{% for x in items %}{{ j() }}{{ x }}{% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("a, b, c");
    }
  }

  @Nested
  class Cycler {

    @Test
    void cyclesThroughProvidedValues() {
      var tmpl = env.fromString("{% set c = cycler('a', 'b', 'c') %}{% for x in items %}{{ c.next() }}{% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of(1, 2, 3, 4, 5)))).isEqualTo("abcab");
    }

    @Test
    void cyclerWithoutValuesHasEmptyCurrent() {
      assertThat(env.fromString("{% set c = cycler() %}{{ c.current }}").render()).isEqualTo("");
    }
  }

  @Nested
  class TextGenerators {

    @Test
    void lipsumProducesLoremIpsumText() {
      assertThat(env.fromString("{{ lipsum() }}").render()).contains("Lorem ipsum");
    }

    @Test
    void strftimeNowFormatsCurrentYear() {
      assertThat(env.fromString("{{ strftime_now('%Y') }}").render()).matches("\\d{4}");
    }

    @Test
    void strftimeNowDefaultFormatIsIsoDate() {
      assertThat(env.fromString("{{ strftime_now() }}").render()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void strftimeNowSupportsCompoundPatterns() {
      assertThat(env.fromString("{{ strftime_now('%Y/%m/%d') }}").render()).matches("\\d{4}/\\d{2}/\\d{2}");
      assertThat(env.fromString("{{ strftime_now('%H:%M:%S') }}").render()).matches("\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void strftimeNowInterpolatesInsideLiteralText() {
      // Pattern letters like 'd' inside literal prose must not be interpreted.
      assertThat(env.fromString("{{ strftime_now('today: %Y-%m-%d end') }}").render()).matches(
        "today: \\d{4}-\\d{2}-\\d{2} end"
      );
    }
  }

  @Nested
  class StrftimeFormatTranslation {

    // These tests exercise the Python → Java translator directly so we can
    // assert expected patterns without depending on wall-clock output.
    // The translator only quotes *literal runs* between directives, so a
    // format that starts with a directive has no leading quotes.

    @Test
    void translatesAllBasicDirectives() {
      assertThat(Environment.pythonToJavaDateFormat("%Y-%m-%d")).isEqualTo("yyyy'-'MM'-'dd");
    }

    @Test
    void translatesTimeOfDay() {
      assertThat(Environment.pythonToJavaDateFormat("%H:%M:%S")).isEqualTo("HH':'mm':'ss");
    }

    @Test
    void translatesTwoDigitYearAndTwelveHour() {
      assertThat(Environment.pythonToJavaDateFormat("%y %I%p")).isEqualTo("yy' 'hha");
    }

    @Test
    void translatesTextualDayAndMonth() {
      assertThat(Environment.pythonToJavaDateFormat("%A %B %a %b")).isEqualTo("EEEE' 'MMMM' 'EEE' 'MMM");
    }

    @Test
    void translatesDayOfYear() {
      assertThat(Environment.pythonToJavaDateFormat("%j")).isEqualTo("DDD");
    }

    @Test
    void translatesCompositeDirectives() {
      assertThat(Environment.pythonToJavaDateFormat("%c")).isEqualTo("EEE MMM dd HH:mm:ss yyyy");
      assertThat(Environment.pythonToJavaDateFormat("%x")).isEqualTo("MM/dd/yy");
      assertThat(Environment.pythonToJavaDateFormat("%X")).isEqualTo("HH:mm:ss");
    }

    @Test
    void nonPaddedDirectivesTakePrecedenceOverPadded() {
      // %-d must be resolved as the non-padded day directive, not as %d
      // followed by a literal '-'. With the old chained .replace() approach,
      // "%-d" would match ".replace('%d', 'dd')" first and become "%-dd".
      assertThat(Environment.pythonToJavaDateFormat("%-d/%-m/%Y")).isEqualTo("d'/'M'/'yyyy");
    }

    @Test
    void literalPercentIsEmittedAsSinglePercent() {
      // %% inside a run of literal text becomes a single '%' within one quoted run.
      assertThat(Environment.pythonToJavaDateFormat("%% done")).isEqualTo("'% done'");
    }

    @Test
    void preservesUnknownDirectivesVerbatim() {
      // %q is not a real directive — leave it as literal text.
      assertThat(Environment.pythonToJavaDateFormat("%Y %q end")).isEqualTo("yyyy' %q end'");
    }

    @Test
    void escapesSingleQuotesInLiteralText() {
      // A single quote inside literal text needs to be doubled for DateTimeFormatter.
      assertThat(Environment.pythonToJavaDateFormat("it's %Y")).isEqualTo("'it''s 'yyyy");
    }

    @Test
    void emptyFormatProducesEmptyPattern() {
      assertThat(Environment.pythonToJavaDateFormat("")).isEqualTo("");
    }

    @Test
    void trailingDanglingPercentPreservedVerbatim() {
      // A trailing, dangling '%' has no directive to claim it — stays as a literal.
      assertThat(Environment.pythonToJavaDateFormat("%Y%")).isEqualTo("yyyy'%'");
    }
  }

  @Nested
  class RaiseException {

    @Test
    void raiseExceptionThrowsTemplateExceptionWithMessage() {
      assertThatThrownBy(() -> env.fromString("{{ raise_exception('boom') }}").render())
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("boom");
    }

    @Test
    void raiseExceptionWithoutArgUsesFallbackMessage() {
      assertThatThrownBy(() -> env.fromString("{{ raise_exception() }}").render())
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Template error");
    }
  }

  @Nested
  class UnknownHelpers {

    @Test
    void unknownFilterThrows() {
      assertThatThrownBy(() -> env.fromString("{{ x | foobar }}").render(Map.of("x", 1)))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Unknown filter");
    }
  }
}
