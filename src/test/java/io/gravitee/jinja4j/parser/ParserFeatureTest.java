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
package io.gravitee.jinja4j.parser;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Parser feature coverage: statement and expression forms produced by templates.
 */
class ParserFeatureTest {

  private final Environment env = new Environment();

  @Nested
  class Blocks {

    @Test
    void filterBlock() {
      assertThat(env.fromString("{% filter upper %}hello world{% endfilter %}").render()).isEqualTo("HELLO WORLD");
    }

    @Test
    void filterBlockWithArgs() {
      assertThat(env.fromString("{% filter replace('world', 'there') %}hello world{% endfilter %}").render()).isEqualTo(
        "hello there"
      );
    }

    @Test
    void rawBlockRendersContentLiterally() {
      assertThat(env.fromString("{% raw %}hello world{% endraw %}").render()).isEqualTo("hello world");
    }

    @Test
    void rawBlockPreservesTemplateSyntax() {
      assertThat(env.fromString("{% raw %}{{x}}{% endraw %}").render()).contains("{{").contains("}}");
    }

    @Test
    void rawBlockPreservesStatementTags() {
      var out = env.fromString("{% raw %}{%if true%}yes{%endif%}{% endraw %}").render();
      assertThat(out).contains("{%").contains("%}");
    }

    @Test
    void withBlockSupportsMultipleAssignments() {
      assertThat(env.fromString("{% with a = 1, b = 2 %}{{ a }}+{{ b }}{% endwith %}").render()).isEqualTo("1+2");
    }

    @Test
    void blockWithNamedEndblock() {
      env.addTemplate("base.html", "{% block content %}default{% endblock content %}");
      assertThat(env.getTemplate("base.html").render()).isEqualTo("default");
    }

    @Test
    void standaloneBlockRendersOriginalBody() {
      assertThat(env.fromString("{% block title %}Original{% endblock %}").render()).isEqualTo("Original");
    }

    @Test
    void childBlocksOverrideParentSelectively() {
      env.addTemplate(
        "base.html",
        "{% block title %}Default Title{% endblock %}--{% block content %}Default Content{% endblock %}"
      );
      env.addTemplate("child.html", "{% extends 'base.html' %}{% block title %}Custom Title{% endblock %}");
      assertThat(env.getTemplate("child.html").render()).isEqualTo("Custom Title--Default Content");
    }
  }

  @Nested
  class Literals {

    @Test
    void noneLiteralIsFalsy() {
      assertThat(env.fromString("{% if none %}yes{% else %}no{% endif %}").render()).isEqualTo("no");
    }

    @Test
    void noneAlsoSatisfiesIsNoneTest() {
      var ctx = new java.util.HashMap<String, Object>();
      ctx.put("x", null);
      assertThat(env.fromString("{{ x is none }}").render(ctx)).isEqualTo("True");
    }

    @Test
    void emptyTupleLiteralHasZeroLength() {
      assertThat(env.fromString("{{ () | length }}").render()).isEqualTo("0");
    }

    @Test
    void tupleInForLoopUnpacks() {
      assertThat(env.fromString("{% for a, b in [(1, 2), (3, 4)] %}{{ a }}+{{ b }} {% endfor %}").render()).isEqualTo(
        "1+2 3+4 "
      );
    }
  }

  @Nested
  class Operators {

    @Test
    void unaryPlusLeavesNumberUnchanged() {
      assertThat(env.fromString("{{ +5 }}").render()).isEqualTo("5");
    }

    @Test
    void ternaryWithoutElseRendersEmptyWhenFalse() {
      assertThat(env.fromString("{{ 'yes' if false }}").render()).isEqualTo("");
    }

    @Test
    void tildeConcatenatesAsString() {
      assertThat(env.fromString("{{ 'hello' ~ 'world' }}").render()).isEqualTo("helloworld");
      assertThat(env.fromString("{{ 42 ~ ' items' }}").render()).isEqualTo("42 items");
    }
  }

  @Nested
  class TrailingCommas {

    @Test
    void inArgumentList() {
      assertThat(env.fromString("{{ range(3,) | join }}").render()).isEqualTo("012");
    }

    @Test
    void inListLiteral() {
      assertThat(env.fromString("{{ [1, 2, 3,] | join }}").render()).isEqualTo("123");
    }

    @Test
    void inDictLiteral() {
      assertThat(env.fromString("{% set d = {'a': 1, 'b': 2,} %}{{ d.a }}").render()).isEqualTo("1");
    }

    @Test
    void inTupleLiteral() {
      assertThat(env.fromString("{% for a, b in [(1, 2,)] %}{{ a }}+{{ b }}{% endfor %}").render()).isEqualTo("1+2");
    }
  }

  @Nested
  class Slicing {

    @Test
    void startAndStopWithoutStep() {
      assertThat(env.fromString("{{ items[0:2] | join }}").render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("ab");
    }
  }

  @Nested
  class ControlFlow {

    @Test
    void elseTokenInsideForIsForElse() {
      var tmpl = env.fromString("{% for x in items %}{{ x }}{% else %}empty{% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of()))).isEqualTo("empty");
      assertThat(tmpl.render(Map.of("items", List.of("a", "b")))).isEqualTo("ab");
    }

    @Test
    void elifChainChoosesFirstMatch() {
      var tmpl = env.fromString("{% if x == 1 %}one{% elif x == 2 %}two{% elif x == 3 %}three{% else %}other{% endif %}");
      assertThat(tmpl.render(Map.of("x", 1))).isEqualTo("one");
      assertThat(tmpl.render(Map.of("x", 2))).isEqualTo("two");
      assertThat(tmpl.render(Map.of("x", 3))).isEqualTo("three");
      assertThat(tmpl.render(Map.of("x", 99))).isEqualTo("other");
    }

    @Test
    void recursiveKeywordIsAccepted() {
      var tmpl = env.fromString("{% for x in items recursive %}{{ x }}{% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of("a", "b")))).isEqualTo("ab");
    }
  }

  @Nested
  class IsTestNames {

    @Test
    void acceptsTrueKeywordAsTestName() {
      assertThat(env.fromString("{{ true is true }}").render()).isEqualTo("True");
    }

    @Test
    void acceptsFalseKeywordAsTestName() {
      assertThat(env.fromString("{{ false is false }}").render()).isEqualTo("True");
    }
  }

  @Nested
  class Includes {

    @Test
    void ignoreMissingSkipsAbsentTemplate() {
      assertThat(env.fromString("{% include 'missing.txt' ignore missing %}OK").render()).isEqualTo("OK");
    }
  }

  @Nested
  class Macros {

    @Test
    void callWithKeywordArguments() {
      var tmpl = env.fromString(
        "{% macro greet(name, greeting='Hello') %}{{ greeting }} {{ name }}{% endmacro %}{{ greet(greeting='Hi', name='Bob') }}"
      );
      assertThat(tmpl.render()).isEqualTo("Hi Bob");
    }

    @Test
    void missingPositionalArgsDefaultToUndefined() {
      var tmpl = env.fromString("{% macro m(a, b) %}{{ a }}-{{ b }}{% endmacro %}{{ m('x') }}");
      assertThat(tmpl.render()).isEqualTo("x-");
    }
  }
}
