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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.TemplateException;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the {@code split}, {@code regex_first} and {@code regex_findall} filters. */
class RegexAndSplitFiltersTest {

  private final Environment env = new Environment();

  private String render(String source, Map<String, Object> ctx) {
    return env.fromString(source).render(ctx);
  }

  private String render(String source) {
    return render(source, Map.of());
  }

  @Nested
  class Split {

    @Test
    void splitsOnWhitespaceByDefault() {
      assertThat(render("{{ '  a  b\\tc ' | split | join('|') }}")).isEqualTo("a|b|c");
    }

    @Test
    void splitsOnSeparator() {
      assertThat(render("{{ 'a,b,,c' | split(',') | join('|') }}")).isEqualTo("a|b||c");
    }

    @Test
    void separatorAbsentYieldsWholeString() {
      assertThat(render("{{ 'abc' | split(',') | join('|') }}")).isEqualTo("abc");
    }

    @Test
    void maxsplitLimitsSplits() {
      assertThat(render("{{ 'a,b,c,d' | split(',', 2) | join('|') }}")).isEqualTo("a|b|c,d");
    }

    @Test
    void whitespaceSplitWithMaxsplitKeepsRemainder() {
      assertThat(render("{{ 'a b c d' | split(none, 1) | join('|') }}")).isEqualTo("a|b c d");
    }

    @Test
    void resultIsAListUsableInForLoops() {
      assertThat(render("{% for p in 'x;y' | split(';') %}[{{ p }}]{% endfor %}")).isEqualTo("[x][y]");
    }
  }

  @Nested
  class RegexFirst {

    @Test
    void returnsFullMatchByDefault() {
      assertThat(render("{{ 'abc 123 def' | regex_first('\\\\d+') }}")).isEqualTo("123");
    }

    @Test
    void returnsRequestedGroup() {
      assertThat(render("{{ 'call:send{a:1}' | regex_first('call:(\\\\w+)', 1) }}")).isEqualTo("send");
    }

    @Test
    void noMatchYieldsNone() {
      assertThat(render("{{ ('abc' | regex_first('\\\\d+')) is none }}")).isEqualTo("True");
    }

    @Test
    void unmatchedGroupYieldsNone() {
      assertThat(render("{{ ('ab' | regex_first('a(x)?b', 1)) is none }}")).isEqualTo("True");
    }

    @Test
    void inlineDotallFlagWorks() {
      assertThat(render("{{ 'x<t>a\\nb</t>y' | regex_first('(?s)<t>(.*?)</t>', 1) }}")).isEqualTo("a\nb");
    }

    @Test
    void invalidPatternThrows() {
      assertThatThrownBy(() -> render("{{ 'x' | regex_first('(') }}")).isInstanceOf(TemplateException.class);
    }
  }

  @Nested
  class RegexFindall {

    @Test
    void noGroupsReturnsFullMatches() {
      assertThat(render("{{ 'a1 b22 c333' | regex_findall('\\\\d+') | join(',') }}")).isEqualTo("1,22,333");
    }

    @Test
    void oneGroupReturnsThatGroup() {
      assertThat(render("{{ '<p=a>1</p><p=b>2</p>' | regex_findall('<p=(\\\\w+)>') | join(',') }}")).isEqualTo("a,b");
    }

    @Test
    void multipleGroupsReturnListsOfGroups() {
      assertThat(
        render(
          "{% for m in '<p=a>1</p><p=b>2</p>' | regex_findall('<p=(\\\\w+)>(\\\\d)</p>') %}" +
            "{{ m[0] }}={{ m[1] }};{% endfor %}"
        )
      ).isEqualTo("a=1;b=2;");
    }

    @Test
    void unmatchedAlternativeGroupIsNone() {
      assertThat(
        render(
          "{% for m in 'x=1,y=\"q\"' | regex_findall('(\\\\w+)=(?:\"(\\\\w+)\"|(\\\\d+))') %}" +
            "{{ m[0] }}:{{ 'S' if m[1] is not none else 'B' }};{% endfor %}"
        )
      ).isEqualTo("x:B;y:S;");
    }

    @Test
    void forcedGroupOverridesDefaultSemantics() {
      assertThat(render("{{ '<a><b>' | regex_findall('<(\\\\w)>', 0) | join(',') }}")).isEqualTo("<a>,<b>");
    }

    @Test
    void noMatchesYieldEmptyList() {
      assertThat(render("{{ 'abc' | regex_findall('\\\\d') | length }}")).isEqualTo("0");
    }
  }
}
