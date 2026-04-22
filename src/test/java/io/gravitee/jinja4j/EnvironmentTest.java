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

import io.gravitee.jinja4j.value.Value;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for core Jinja4j template engine features.
 */
class EnvironmentTest {

  private Environment env;

  @BeforeEach
  void setUp() {
    env = new Environment();
  }

  // ---- Basic rendering ----

  @Test
  void renderPlainText() {
    var tmpl = env.fromString("Hello World!");
    assertThat(tmpl.render()).isEqualTo("Hello World!");
  }

  @Test
  void renderVariable() {
    var tmpl = env.fromString("Hello {{ name }}!");
    assertThat(tmpl.render(Map.of("name", "World"))).isEqualTo("Hello World!");
  }

  @Test
  void renderAddTemplate() {
    env.addTemplate("hello.txt", "Hello {{ name }}!");
    var tmpl = env.getTemplate("hello.txt");
    assertThat(tmpl.render(Map.of("name", "World"))).isEqualTo("Hello World!");
  }

  @Test
  void renderContextBuilder() {
    var tmpl = env.fromString("{{ a }} + {{ b }}");
    assertThat(tmpl.render(Context.of("a", 1).and("b", 2))).isEqualTo("1 + 2");
  }

  // ---- Variable access ----

  @Test
  void renderDotAccess() {
    var tmpl = env.fromString("{{ user.name }}");
    assertThat(tmpl.render(Map.of("user", Map.of("name", "Alice")))).isEqualTo("Alice");
  }

  @Test
  void renderBracketAccess() {
    var tmpl = env.fromString("{{ user['name'] }}");
    assertThat(tmpl.render(Map.of("user", Map.of("name", "Alice")))).isEqualTo("Alice");
  }

  @Test
  void renderListIndex() {
    var tmpl = env.fromString("{{ items[0] }}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("a");
  }

  @Test
  void renderNegativeIndex() {
    var tmpl = env.fromString("{{ items[-1] }}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("c");
  }

  // ---- Arithmetic ----

  @ParameterizedTest
  @CsvSource(
    {
      "{{ 1 + 2 }}, 3",
      "{{ 10 - 3 }}, 7",
      "{{ 3 * 4 }}, 12",
      "{{ 10 / 3 }}, 3.3333333333333335",
      "{{ 10 // 3 }}, 3",
      "{{ 10 % 3 }}, 1",
      "{{ 2 ** 10 }}, 1024",
    }
  )
  void arithmetic(String template, String expected) {
    assertThat(env.fromString(template).render()).isEqualTo(expected);
  }

  // ---- Comparisons ----

  @ParameterizedTest
  @CsvSource(
    {
      "{{ 1 == 1 }}, True",
      "{{ 1 != 2 }}, True",
      "{{ 1 < 2 }}, True",
      "{{ 2 > 1 }}, True",
      "{{ 1 <= 1 }}, True",
      "{{ 1 >= 1 }}, True",
      "{{ 2 < 1 }}, False",
    }
  )
  void comparisons(String template, String expected) {
    assertThat(env.fromString(template).render()).isEqualTo(expected);
  }

  // ---- Boolean logic ----

  @Test
  void booleanAnd() {
    var tmpl = env.fromString("{{ true and true }}");
    assertThat(tmpl.render()).isEqualTo("True");
  }

  @Test
  void booleanOr() {
    var tmpl = env.fromString("{{ false or true }}");
    assertThat(tmpl.render()).isEqualTo("True");
  }

  @Test
  void booleanNot() {
    var tmpl = env.fromString("{{ not false }}");
    assertThat(tmpl.render()).isEqualTo("True");
  }

  // ---- String concatenation ----

  @Test
  void stringConcat() {
    var tmpl = env.fromString("{{ 'hello' ~ ' ' ~ 'world' }}");
    assertThat(tmpl.render()).isEqualTo("hello world");
  }

  @Test
  void stringConcatWithPlus() {
    var tmpl = env.fromString("{{ 'hello' + ' world' }}");
    assertThat(tmpl.render()).isEqualTo("hello world");
  }

  // ---- Ternary / inline if ----

  @Test
  void ternaryTrue() {
    var tmpl = env.fromString("{{ 'yes' if true else 'no' }}");
    assertThat(tmpl.render()).isEqualTo("yes");
  }

  @Test
  void ternaryFalse() {
    var tmpl = env.fromString("{{ 'yes' if false else 'no' }}");
    assertThat(tmpl.render()).isEqualTo("no");
  }

  // ---- If statements ----

  @Test
  void ifTrue() {
    var tmpl = env.fromString("{% if true %}yes{% endif %}");
    assertThat(tmpl.render()).isEqualTo("yes");
  }

  @Test
  void ifFalse() {
    var tmpl = env.fromString("{% if false %}yes{% endif %}");
    assertThat(tmpl.render()).isEqualTo("");
  }

  @Test
  void ifElse() {
    var tmpl = env.fromString("{% if false %}yes{% else %}no{% endif %}");
    assertThat(tmpl.render()).isEqualTo("no");
  }

  @Test
  void ifElif() {
    var tmpl = env.fromString("{% if false %}a{% elif true %}b{% else %}c{% endif %}");
    assertThat(tmpl.render()).isEqualTo("b");
  }

  // ---- For loops ----

  @Test
  void forLoop() {
    var tmpl = env.fromString("{% for i in items %}{{ i }} {% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("a b c ");
  }

  @Test
  void forLoopIndex() {
    var tmpl = env.fromString("{% for i in items %}{{ loop.index }}{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("123");
  }

  @Test
  void forLoopIndex0() {
    var tmpl = env.fromString("{% for i in items %}{{ loop.index0 }}{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("012");
  }

  @Test
  void forLoopFirst() {
    var tmpl = env.fromString("{% for i in items %}{% if loop.first %}FIRST{% endif %}{{ i }}{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("FIRSTabc");
  }

  @Test
  void forLoopLast() {
    var tmpl = env.fromString("{% for i in items %}{{ i }}{% if loop.last %} LAST{% endif %}{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("abc LAST");
  }

  @Test
  void forLoopLength() {
    var tmpl = env.fromString("{% for i in items %}{{ loop.length }}{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("333");
  }

  @Test
  void forElse() {
    var tmpl = env.fromString("{% for i in items %}{{ i }}{% else %}empty{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of()))).isEqualTo("empty");
  }

  @Test
  void forLoopUnpack() {
    var tmpl = env.fromString("{% for k, v in items %}{{ k }}={{ v }} {% endfor %}");
    var items = List.of(List.of("a", "1"), List.of("b", "2"));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("a=1 b=2 ");
  }

  // ---- Set statement ----

  @Test
  void setVariable() {
    var tmpl = env.fromString("{% set x = 42 %}{{ x }}");
    assertThat(tmpl.render()).isEqualTo("42");
  }

  // ---- With block ----

  @Test
  void withBlock() {
    var tmpl = env.fromString("{% with x = 42 %}{{ x }}{% endwith %}");
    assertThat(tmpl.render()).isEqualTo("42");
  }

  // ---- Filters ----

  @Test
  void filterUpper() {
    assertThat(env.fromString("{{ 'hello' | upper }}").render()).isEqualTo("HELLO");
  }

  @Test
  void filterLower() {
    assertThat(env.fromString("{{ 'HELLO' | lower }}").render()).isEqualTo("hello");
  }

  @Test
  void filterCapitalize() {
    assertThat(env.fromString("{{ 'hello world' | capitalize }}").render()).isEqualTo("Hello world");
  }

  @Test
  void filterTitle() {
    assertThat(env.fromString("{{ 'hello world' | title }}").render()).isEqualTo("Hello World");
  }

  @Test
  void filterTrim() {
    assertThat(env.fromString("{{ '  hello  ' | trim }}").render()).isEqualTo("hello");
  }

  @Test
  void filterJoin() {
    var tmpl = env.fromString("{{ items | join(', ') }}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("a, b, c");
  }

  @Test
  void filterDefault() {
    assertThat(env.fromString("{{ x | default('fallback') }}").render()).isEqualTo("fallback");
  }

  @Test
  void filterDefaultWithValue() {
    assertThat(env.fromString("{{ x | default('fallback') }}").render(Map.of("x", "val"))).isEqualTo("val");
  }

  @Test
  void filterLength() {
    assertThat(env.fromString("{{ items | length }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo("3");
  }

  @Test
  void filterReplace() {
    assertThat(env.fromString("{{ 'hello world' | replace('world', 'jinja') }}").render()).isEqualTo("hello jinja");
  }

  @Test
  void filterReverse() {
    assertThat(env.fromString("{{ 'hello' | reverse }}").render()).isEqualTo("olleh");
  }

  @Test
  void filterSort() {
    var tmpl = env.fromString("{{ items | sort | join(', ') }}");
    assertThat(tmpl.render(Map.of("items", List.of("c", "a", "b")))).isEqualTo("a, b, c");
  }

  @Test
  void filterFirst() {
    assertThat(env.fromString("{{ items | first }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("a");
  }

  @Test
  void filterLast() {
    assertThat(env.fromString("{{ items | last }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("b");
  }

  @Test
  void filterInt() {
    assertThat(env.fromString("{{ '42' | int }}").render()).isEqualTo("42");
  }

  @Test
  void filterFloat() {
    assertThat(env.fromString("{{ '3.14' | float }}").render()).isEqualTo("3.14");
  }

  @Test
  void filterString() {
    assertThat(env.fromString("{{ 42 | string }}").render()).isEqualTo("42");
  }

  @Test
  void filterAbs() {
    // In Jinja2, | has higher precedence than unary minus, so -5 | abs = -(abs(5)) = -5
    // Use parentheses to get the expected behavior
    assertThat(env.fromString("{{ (-5) | abs }}").render()).isEqualTo("5");
  }

  @Test
  void filterRound() {
    assertThat(env.fromString("{{ 3.7 | round }}").render()).isEqualTo("4.0");
  }

  @Test
  void chainedFilters() {
    assertThat(env.fromString("{{ '  hello  ' | trim | upper }}").render()).isEqualTo("HELLO");
  }

  // ---- Tests (is expressions) ----

  @Test
  void testDefined() {
    assertThat(env.fromString("{{ x is defined }}").render(Map.of("x", 1))).isEqualTo("True");
  }

  @Test
  void testUndefined() {
    assertThat(env.fromString("{{ x is undefined }}").render()).isEqualTo("True");
  }

  @Test
  void testNone() {
    var ctx = new HashMap<String, Object>();
    ctx.put("x", null);
    assertThat(env.fromString("{{ x is none }}").render(ctx)).isEqualTo("True");
  }

  @Test
  void testOddEven() {
    assertThat(env.fromString("{{ 3 is odd }}").render()).isEqualTo("True");
    assertThat(env.fromString("{{ 4 is even }}").render()).isEqualTo("True");
  }

  @Test
  void testDivisibleby() {
    assertThat(env.fromString("{{ 10 is divisibleby(5) }}").render()).isEqualTo("True");
    assertThat(env.fromString("{{ 10 is divisibleby(3) }}").render()).isEqualTo("False");
  }

  @Test
  void testIsNot() {
    assertThat(env.fromString("{{ x is not defined }}").render()).isEqualTo("True");
  }

  // ---- Membership tests ----

  @Test
  void inOperator() {
    var tmpl = env.fromString("{{ 'a' in items }}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("True");
  }

  @Test
  void notInOperator() {
    var tmpl = env.fromString("{{ 'd' not in items }}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("True");
  }

  // ---- Global functions ----

  @Test
  void rangeFunction() {
    var tmpl = env.fromString("{% for i in range(3) %}{{ i }}{% endfor %}");
    assertThat(tmpl.render()).isEqualTo("012");
  }

  @Test
  void rangeFunctionWithStartStop() {
    var tmpl = env.fromString("{% for i in range(1, 4) %}{{ i }}{% endfor %}");
    assertThat(tmpl.render()).isEqualTo("123");
  }

  @Test
  void namespaceFunction() {
    var tmpl = env.fromString(
      "{% set ns = namespace(count=0) %}{% for i in items %}{% set ns.count = ns.count + 1 %}{% endfor %}{{ ns.count }}"
    );
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("3");
  }

  @Test
  void dictFunction() {
    var tmpl = env.fromString("{% set d = dict(a=1, b=2) %}{{ d.a }},{{ d.b }}");
    assertThat(tmpl.render()).isEqualTo("1,2");
  }

  @Test
  void raiseException() {
    var tmpl = env.fromString("{{ raise_exception('bad') }}");
    assertThatThrownBy(tmpl::render).isInstanceOf(TemplateException.class).hasMessageContaining("bad");
  }

  // ---- Whitespace control ----

  @Test
  void whitespaceControlLeft() {
    var tmpl = env.fromString("  {%- if true %}yes{% endif %}");
    assertThat(tmpl.render()).isEqualTo("yes");
  }

  @Test
  void whitespaceControlRight() {
    var tmpl = env.fromString("{% if true -%}  yes{% endif %}");
    assertThat(tmpl.render()).isEqualTo("yes");
  }

  @Test
  void whitespaceControlExpr() {
    var tmpl = env.fromString("  {{- 'x' -}}  ");
    assertThat(tmpl.render()).isEqualTo("x");
  }

  // ---- Comments ----

  @Test
  void comments() {
    var tmpl = env.fromString("hello{# this is a comment #} world");
    assertThat(tmpl.render()).isEqualTo("hello world");
  }

  // ---- Macros ----

  @Test
  void macroSimple() {
    var tmpl = env.fromString("{% macro greet(name) %}Hello {{ name }}!{% endmacro %}{{ greet('World') }}");
    assertThat(tmpl.render()).isEqualTo("Hello World!");
  }

  @Test
  void macroWithDefault() {
    var tmpl = env.fromString("{% macro greet(name='World') %}Hello {{ name }}!{% endmacro %}{{ greet() }}");
    assertThat(tmpl.render()).isEqualTo("Hello World!");
  }

  // ---- Include ----

  @Test
  void includeTemplate() {
    env.addTemplate("partial.txt", "Hello {{ name }}!");
    var tmpl = env.fromString("{% include 'partial.txt' %}");
    assertThat(tmpl.render(Map.of("name", "World"))).isEqualTo("Hello World!");
  }

  // ---- Extends / Block ----

  @Test
  void extendsAndBlock() {
    env.addTemplate("base.html", "Before {% block content %}default{% endblock %} After");
    env.addTemplate("child.html", "{% extends 'base.html' %}{% block content %}overridden{% endblock %}");
    var tmpl = env.getTemplate("child.html");
    assertThat(tmpl.render()).isEqualTo("Before overridden After");
  }

  // ---- Custom filters and functions ----

  @Test
  void customFilter() {
    env.addFilter("double", (v, args, kwargs, loc) -> Value.of(v.asString() + v.asString()));
    var tmpl = env.fromString("{{ 'ha' | double }}");
    assertThat(tmpl.render()).isEqualTo("haha");
  }

  @Test
  void customGlobal() {
    env.addGlobal("pi", 3.14159);
    var tmpl = env.fromString("{{ pi }}");
    assertThat(tmpl.render()).isEqualTo("3.14159");
  }

  @Test
  void customFunction() {
    env.addFunction("add", (args, kwargs) -> Value.of(args.get(0).asLong() + args.get(1).asLong()));
    var tmpl = env.fromString("{{ add(1, 2) }}");
    assertThat(tmpl.render()).isEqualTo("3");
  }

  // ---- List and dict literals ----

  @Test
  void listLiteral() {
    var tmpl = env.fromString("{{ [1, 2, 3] | join(', ') }}");
    assertThat(tmpl.render()).isEqualTo("1, 2, 3");
  }

  @Test
  void dictLiteral() {
    var tmpl = env.fromString("{% set d = {'a': 1, 'b': 2} %}{{ d.a }},{{ d.b }}");
    assertThat(tmpl.render()).isEqualTo("1,2");
  }

  // ---- Error handling ----

  @Test
  void templateNotFound() {
    assertThatThrownBy(() -> env.getTemplate("nonexistent.html"))
      .isInstanceOf(TemplateException.class)
      .hasMessageContaining("not found");
  }

  @Test
  void syntaxError() {
    assertThatThrownBy(() -> env.fromString("{{ }}")).isInstanceOf(TemplateException.class);
  }

  // ---- Auto-escaping ----

  @Test
  void autoEscapingOff() {
    var tmpl = env.fromString("{{ text }}");
    assertThat(tmpl.render(Map.of("text", "<b>bold</b>"))).isEqualTo("<b>bold</b>");
  }

  @Test
  void autoEscapingOn() {
    env.setAutoEscaping(true);
    var tmpl = env.fromString("{{ text }}");
    assertThat(tmpl.render(Map.of("text", "<b>bold</b>"))).isEqualTo("&lt;b&gt;bold&lt;/b&gt;");
  }

  @Test
  void safeFilter() {
    env.setAutoEscaping(true);
    var tmpl = env.fromString("{{ text | safe }}");
    assertThat(tmpl.render(Map.of("text", "<b>bold</b>"))).isEqualTo("<b>bold</b>");
  }

  // ---- Generation tag (no-op) ----

  @Test
  void generationTag() {
    var tmpl = env.fromString("before{% generation %}after");
    assertThat(tmpl.render()).isEqualTo("beforeafter");
  }

  // ---- Namespace with mutable state ----

  @Test
  void namespaceMutability() {
    var tmpl = env.fromString(
      """
      {% set ns = namespace(found=false) %}\
      {% for item in items %}\
      {% if item == 'target' %}{% set ns.found = true %}{% endif %}\
      {% endfor %}\
      {{ ns.found }}"""
    );
    assertThat(tmpl.render(Map.of("items", List.of("a", "target", "b")))).isEqualTo("True");
  }

  // ---- Complex expressions ----

  @Test
  void compoundBooleanCondition() {
    var tmpl = env.fromString("{% if loop_last and add_gen %}PROMPT{% endif %}");
    assertThat(tmpl.render(Map.of("loop_last", true, "add_gen", true))).isEqualTo("PROMPT");
  }

  @Test
  void stringConcatInExpression() {
    var tmpl = env.fromString("{{ '<|' + role + '|>' }}");
    assertThat(tmpl.render(Map.of("role", "user"))).isEqualTo("<|user|>");
  }
}
