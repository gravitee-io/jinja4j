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

import io.gravitee.jinja4j.chat.ChatTemplateRenderer;
import io.gravitee.jinja4j.chat.Message;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Tests exercising Jinja2 idioms found in chat templates.
 *
 * <p>Each test targets a specific idiom.</p>
 */
class TemplateIdiomTest {

  private Environment env;

  @BeforeEach
  void setUp() {
    env = new Environment();
  }

  // ---- loop.previtem / loop.nextitem ----

  @Test
  void loopPrevitem() {
    var tmpl = env.fromString(
      "{% for x in items %}" +
        "{% if loop.previtem is defined %}prev={{ loop.previtem }},{% endif %}" +
        "cur={{ x }} " +
        "{% endfor %}"
    );
    var result = tmpl.render(Map.of("items", List.of("a", "b", "c")));
    assertThat(result).isEqualTo("cur=a prev=a,cur=b prev=b,cur=c ");
  }

  @Test
  void loopNextitem() {
    var tmpl = env.fromString(
      "{% for x in items %}" + "{{ x }}" + "{% if loop.nextitem is defined %}-{% endif %}" + "{% endfor %}"
    );
    var result = tmpl.render(Map.of("items", List.of("a", "b", "c")));
    assertThat(result).isEqualTo("a-b-c");
  }

  @Test
  void loopPrevitemNextitemForGrouping() {
    var tmpl = env.fromString(
      "{% for msg in messages %}" +
        "{% if msg.role == 'tool' %}" +
        "{% if loop.previtem is not defined or loop.previtem.role != 'tool' %}[TOOLS:{% endif %}" +
        "{{ msg.content }}" +
        "{% if loop.nextitem is not defined or loop.nextitem.role != 'tool' %}]{% endif %}" +
        "{% else %}" +
        "{{ msg.content }}" +
        "{% endif %}" +
        "{% endfor %}"
    );
    var messages = List.of(
      Map.of("role", "user", "content", "Hi"),
      Map.of("role", "tool", "content", "result1"),
      Map.of("role", "tool", "content", "result2"),
      Map.of("role", "assistant", "content", "Done")
    );
    var result = tmpl.render(Map.of("messages", messages));
    assertThat(result).isEqualTo("Hi[TOOLS:result1result2]Done");
  }

  // ---- |tojson(indent=N) ----

  @Test
  void tojsonWithIndent() {
    var tmpl = env.fromString("{{ data | tojson(indent=2) }}");
    var data = Map.of("name", "test", "value", 42);
    var result = tmpl.render(Map.of("data", data));
    assertThat(result).contains("\"name\": \"test\"");
    assertThat(result).contains("\"value\": 42");
    assertThat(result).contains("\n"); // Has newlines due to indent
  }

  @Test
  void tojsonCompact() {
    var tmpl = env.fromString("{{ data | tojson }}");
    var result = tmpl.render(Map.of("data", Map.of("a", 1)));
    assertThat(result).doesNotContain("\n");
  }

  @Test
  void tojsonNestedIndent() {
    var tmpl = env.fromString("{{ data | tojson(indent=4) }}");
    var data = Map.of("tool", Map.of("name", "search", "args", List.of("query")));
    var result = tmpl.render(Map.of("data", data));
    assertThat(result).contains("        "); // 8 spaces = nested indent
  }

  // ---- |reject('equalto', value) / |select('equalto', value) ----

  @Test
  void rejectEqualto() {
    var tmpl = env.fromString("{{ items | reject('equalto', 'skip') | join(', ') }}");
    var result = tmpl.render(Map.of("items", List.of("keep", "skip", "also_keep")));
    assertThat(result).isEqualTo("keep, also_keep");
  }

  @Test
  void selectEqualto() {
    var tmpl = env.fromString("{{ items | select('equalto', 'target') | join(', ') }}");
    var result = tmpl.render(Map.of("items", List.of("a", "target", "b", "target")));
    assertThat(result).isEqualTo("target, target");
  }

  @Test
  void rejectWithoutArgs() {
    // No args → reject truthy values, keep falsy
    var tmpl = env.fromString("{{ items | reject | join(', ') }}");
    var result = tmpl.render(Map.of("items", List.of("a", "", "b", "")));
    assertThat(result).isEqualTo(", ");
  }

  @Test
  void selectWithoutArgs() {
    // No args → select truthy values only
    var tmpl = env.fromString("{{ items | select | join(', ') }}");
    var result = tmpl.render(Map.of("items", List.of("a", "", "b", "")));
    assertThat(result).isEqualTo("a, b");
  }

  // ---- |selectattr / |rejectattr with test + value ----

  @Test
  void selectattrTruthy() {
    var tmpl = env.fromString("{% for t in tools | selectattr('enabled') %}{{ t.name }} {% endfor %}");
    var tools = List.of(
      Map.of("name", "search", "enabled", true),
      Map.of("name", "calc", "enabled", false),
      Map.of("name", "browse", "enabled", true)
    );
    var result = tmpl.render(Map.of("tools", tools));
    assertThat(result).isEqualTo("search browse ");
  }

  @Test
  void selectattrEqualto() {
    var tmpl = env.fromString("{% for t in tools | selectattr('type', 'equalto', 'builtin') %}{{ t.name }} {% endfor %}");
    var tools = List.of(
      Map.of("name", "search", "type", "builtin"),
      Map.of("name", "custom_fn", "type", "custom"),
      Map.of("name", "calc", "type", "builtin")
    );
    var result = tmpl.render(Map.of("tools", tools));
    assertThat(result).isEqualTo("search calc ");
  }

  @Test
  void rejectattrEqualto() {
    var tmpl = env.fromString("{% for t in tools | rejectattr('type', 'equalto', 'hidden') %}{{ t.name }} {% endfor %}");
    var tools = List.of(
      Map.of("name", "search", "type", "visible"),
      Map.of("name", "secret", "type", "hidden"),
      Map.of("name", "calc", "type", "visible")
    );
    var result = tmpl.render(Map.of("tools", tools));
    assertThat(result).isEqualTo("search calc ");
  }

  // ---- 'equalto' test ----

  @Test
  void equaltoTest() {
    var tmpl = env.fromString("{{ 42 is equalto(42) }}");
    assertThat(tmpl.render()).isEqualTo("True");
  }

  @Test
  void equaltoTestFalse() {
    var tmpl = env.fromString("{{ 42 is equalto(43) }}");
    assertThat(tmpl.render()).isEqualTo("False");
  }

  // ---- 'in' operator with dict keys ----

  @Test
  void inOperatorDictKeys() {
    var tmpl = env.fromString("{{ 'tool_calls' in message }}");
    var result = tmpl.render(Map.of("message", Map.of("role", "assistant", "tool_calls", List.of())));
    assertThat(result).isEqualTo("True");
  }

  @Test
  void inOperatorDictKeysMissing() {
    var tmpl = env.fromString("{{ 'tool_calls' in message }}");
    var result = tmpl.render(Map.of("message", Map.of("role", "user", "content", "hi")));
    assertThat(result).isEqualTo("False");
  }

  // ---- is mapping / is iterable / is string ----

  @Test
  void isMappingTest() {
    var tmpl = env.fromString("{{ data is mapping }}");
    assertThat(tmpl.render(Map.of("data", Map.of("a", 1)))).isEqualTo("True");
    assertThat(tmpl.render(Map.of("data", "not a map"))).isEqualTo("False");
  }

  @Test
  void isIterableTest() {
    var tmpl = env.fromString("{{ data is iterable }}");
    assertThat(tmpl.render(Map.of("data", List.of(1, 2)))).isEqualTo("True");
    assertThat(tmpl.render(Map.of("data", "string"))).isEqualTo("True");
    assertThat(tmpl.render(Map.of("data", 42))).isEqualTo("False");
  }

  @Test
  void isStringTest() {
    var tmpl = env.fromString("{{ data is string }}");
    assertThat(tmpl.render(Map.of("data", "hello"))).isEqualTo("True");
    assertThat(tmpl.render(Map.of("data", 42))).isEqualTo("False");
  }

  // ---- .title() string method ----

  @Test
  void titleStringMethod() {
    var tmpl = env.fromString("{{ role.title() }}");
    assertThat(tmpl.render(Map.of("role", "user"))).isEqualTo("User");
    assertThat(tmpl.render(Map.of("role", "assistant"))).isEqualTo("Assistant");
    assertThat(tmpl.render(Map.of("role", "system"))).isEqualTo("System");
  }

  // ---- String methods: startswith, endswith, split ----

  @Test
  void startswithMethod() {
    var tmpl = env.fromString("{{ text.startswith('<tool>') }}");
    assertThat(tmpl.render(Map.of("text", "<tool>result</tool>"))).isEqualTo("True");
    assertThat(tmpl.render(Map.of("text", "normal text"))).isEqualTo("False");
  }

  @Test
  void endswithMethod() {
    var tmpl = env.fromString("{{ text.endswith('</tool>') }}");
    assertThat(tmpl.render(Map.of("text", "<tool>result</tool>"))).isEqualTo("True");
  }

  @Test
  void splitMethod() {
    var tmpl = env.fromString("{{ text.split('|') | join(', ') }}");
    assertThat(tmpl.render(Map.of("text", "a|b|c"))).isEqualTo("a, b, c");
  }

  @Test
  void splitAndIndex() {
    var tmpl = env.fromString("{{ text.split('</think>')[-1] }}");
    assertThat(tmpl.render(Map.of("text", "<think>reasoning</think>answer"))).isEqualTo("answer");
  }

  @Test
  void splitWithStripChain() {
    var tmpl = env.fromString("{{ text.split('::')[0].strip() }}");
    assertThat(tmpl.render(Map.of("text", "  key  :: value  "))).isEqualTo("key");
  }

  // ---- set reassigning loop variable (shadowing) ----

  @Test
  void setReassignsLoopVariable() {
    var tmpl = env.fromString(
      "{% for item in items %}" + "{% set item = item.inner %}" + "{{ item.name }} " + "{% endfor %}"
    );
    var items = List.of(Map.of("inner", Map.of("name", "A")), Map.of("inner", Map.of("name", "B")));
    var result = tmpl.render(Map.of("items", items));
    assertThat(result).isEqualTo("A B ");
  }

  // ---- List slicing [1:] ----

  @Test
  void listSliceFromIndex() {
    var tmpl = env.fromString("{% set rest = items[1:] %}{% for x in rest %}{{ x }} {% endfor %}");
    var result = tmpl.render(Map.of("items", List.of("a", "b", "c", "d")));
    assertThat(result).isEqualTo("b c d ");
  }

  // ---- Reverse slicing [::-1] ----

  @Test
  void reverseSlice() {
    var tmpl = env.fromString("{% for x in items[::-1] %}{{ x }} {% endfor %}");
    var result = tmpl.render(Map.of("items", List.of("a", "b", "c")));
    assertThat(result).isEqualTo("c b a ");
  }

  // ---- |length - 1 arithmetic after filter ----

  @Test
  void lengthFilterWithArithmetic() {
    var tmpl = env.fromString("{{ items|length - 1 }}");
    var result = tmpl.render(Map.of("items", List.of("a", "b", "c")));
    assertThat(result).isEqualTo("2");
  }

  // ---- |items filter for dict iteration ----

  @Test
  void itemsFilterForDictUnpacking() {
    var tmpl = env.fromString("{% for k, v in data | items %}{{ k }}={{ v }} {% endfor %}");
    var result = tmpl.render(Map.of("data", Map.of("a", 1, "b", 2)));
    assertThat(result).contains("a=1").contains("b=2");
  }

  // ---- Chained filters: reject + join ----

  @Test
  void chainedRejectJoin() {
    var tmpl = env.fromString("{{ tools | reject('equalto', 'hidden') | join(', ') }}");
    var result = tmpl.render(Map.of("tools", List.of("search", "hidden", "calc")));
    assertThat(result).isEqualTo("search, calc");
  }

  // ---- Recursive macros ----

  @Test
  void recursiveMacro() {
    var tmpl = env.fromString(
      """
      {% macro describe(obj) %}\
      {% if obj is mapping %}\
      {%- for k, v in obj | items %}{{ k }}: {{ describe(v) }}{% endfor %}\
      {% elif obj is sequence %}\
      [{% for item in obj %}{{ describe(item) }}{% if not loop.last %}, {% endif %}{% endfor %}]\
      {% else %}\
      {{ obj }}\
      {% endif %}\
      {% endmacro %}\
      {{ describe(data) }}"""
    );
    var result = tmpl.render(Map.of("data", Map.of("name", "test"))).strip();
    assertThat(result).contains("name:").contains("test");
  }

  // ---- Namespace with multiple attributes ----

  @Test
  void namespaceMultipleAttrs() {
    var tmpl = env.fromString(
      """
      {% set ns = namespace(is_first=true, count=0, system_prompt='') %}\
      {% for msg in messages %}\
      {% if msg.role == 'system' %}{% set ns.system_prompt = msg.content %}\
      {% else %}\
      {% if ns.is_first %}{% set ns.is_first = false %}FIRST:{% endif %}\
      {% set ns.count = ns.count + 1 %}\
      {{ msg.content }}\
      {% endif %}\
      {% endfor %}\
      sys={{ ns.system_prompt }} count={{ ns.count }}"""
    );
    var messages = List.of(
      Map.of("role", "system", "content", "Be helpful"),
      Map.of("role", "user", "content", "Hi"),
      Map.of("role", "assistant", "content", "Hello"),
      Map.of("role", "user", "content", "Bye")
    );
    var result = tmpl.render(Map.of("messages", messages));
    assertThat(result).contains("FIRST:Hi");
    assertThat(result).contains("sys=Be helpful");
    assertThat(result).contains("count=3");
  }

  // ---- is not none on message content ----

  @Test
  void isNotNoneOnContent() {
    var tmpl = env.fromString(
      "{% for msg in messages %}" +
        "{% if msg.content is not none %}{{ msg.content }}{% else %}EMPTY{% endif %} " +
        "{% endfor %}"
    );
    var msgs = new ArrayList<Map<String, Object>>();
    msgs.add(Map.of("content", "hello"));
    var nullMsg = new HashMap<String, Object>();
    nullMsg.put("content", null);
    msgs.add(nullMsg);
    var result = tmpl.render(Map.of("messages", msgs));
    assertThat(result).isEqualTo("hello EMPTY ");
  }

  // ---- Conditional with is defined guard ----

  @Test
  void isDefinedGuard() {
    var tmpl = env.fromString("{% if tools is defined %}HAS_TOOLS{% else %}NO_TOOLS{% endif %}");
    assertThat(tmpl.render(Map.of("tools", List.of("a")))).isEqualTo("HAS_TOOLS");
    assertThat(tmpl.render()).isEqualTo("NO_TOOLS");
  }

  // ---- Complex expression: not x|length == 1 ----

  @Test
  void notFilterEquality() {
    var tmpl = env.fromString("{{ not items|length == 1 }}");
    assertThat(tmpl.render(Map.of("items", List.of("a")))).isEqualTo("False");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b")))).isEqualTo("True");
  }

  // ---- Multi-template array support ----

  @Test
  void multiTemplateArrayParsing() {
    var json = """
      [{"name": "default", "template": "Hello {{ name }}!"},
       {"name": "tool_use", "template": "Tool: {{ name }}"}]""";
    var renderers = ChatTemplateRenderer.fromRawField(json);
    // fromRawField returns the "default" template
    var result = renderers.render(List.of(), Map.of("name", "World"));
    // No messages needed, just test name var
  }

  @Test
  void multiTemplateArrayNamedSelection() {
    var json = """
      [{"name": "default", "template": "Hello {{ name }}!"},
       {"name": "tool_use", "template": "Tool: {{ name }}"}]""";
    var renderer = ChatTemplateRenderer.fromRawField(json, "tool_use");
    var result = renderer.render(List.of(), Map.of("name", "search"));
    assertThat(result).isEqualTo("Tool: search");
  }

  @Test
  void singleStringTemplateAutoDetection() {
    var template = "Hello {{ name }}!";
    var renderer = ChatTemplateRenderer.fromRawField(template);
    var result = renderer.render(List.of(), Map.of("name", "World"));
    assertThat(result).isEqualTo("Hello World!");
  }

  // ---- Full synthetic chat template exercising many idioms at once ----

  @Test
  void syntheticToolCallingTemplate() {
    // Exercises: set, namespace, is defined, is not none, loop.first, loop.last,
    // tojson, in operator on dict, string concat, raise_exception, |trim
    var template = """
      {%- set ns = namespace(has_system=false) -%}
      {%- for message in messages -%}
      {%- if message.role == 'system' -%}
      {%- set ns.has_system = true -%}
      <|system|>{{ message.content | trim }}<|end|>
      {%- elif message.role == 'user' -%}
      <|user|>{{ message.content }}<|end|>
      {%- elif message.role == 'assistant' -%}
      <|assistant|>\
      {%- if message.content is not none -%}
      {{ message.content }}<|end|>
      {%- endif -%}
      {%- if 'tool_calls' in message -%}
      {%- for tc in message.tool_calls -%}
      <tool>{{ tc | tojson }}</tool>
      {%- endfor -%}
      {%- endif -%}
      {%- endif -%}
      {%- endfor -%}
      {%- if add_generation_prompt -%}
      <|assistant|>\
      {%- endif -%}""";

    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("system", "  You are helpful.  "), new Message("user", "Hello"));
    var result = renderer.render(messages, Map.of("add_generation_prompt", true));

    assertThat(result).isEqualTo("<|system|>You are helpful.<|end|>" + "<|user|>Hello<|end|>" + "<|assistant|>");
  }

  // ---- dict.get() method ----

  @Test
  void dictGetMethod() {
    var tmpl = env.fromString("{{ data.get('key1') }}-{{ data.get('missing', 'default') }}");
    var result = tmpl.render(Map.of("data", Map.of("key1", "value1")));
    assertThat(result).isEqualTo("value1-default");
  }

  @Test
  void dictGetMethodReturnsNullForMissing() {
    var tmpl = env.fromString("{% set val = data.get('missing') %}{% if val %}found{% else %}not found{% endif %}");
    var result = tmpl.render(Map.of("data", Map.of("key1", "value1")));
    assertThat(result).isEqualTo("not found");
  }

  @Test
  void dictGetMethodOrChain() {
    var tmpl = env.fromString("{{ data.get('a') or data.get('b') or 'fallback' }}");
    assertThat(tmpl.render(Map.of("data", Map.of("b", "found_b")))).isEqualTo("found_b");
    assertThat(tmpl.render(Map.of("data", Map.of("c", "x")))).isEqualTo("fallback");
  }

  // ---- |map('upper') with string transform ----

  @Test
  void mapFilterWithStringTransform() {
    var tmpl = env.fromString("{{ items | map('upper') | join(', ') }}");
    var result = tmpl.render(Map.of("items", List.of("hello", "world")));
    assertThat(result).isEqualTo("HELLO, WORLD");
  }

  @Test
  void mapFilterWithListMaterialization() {
    var tmpl = env.fromString("{{ items | map('upper') | list | join(', ') }}");
    var result = tmpl.render(Map.of("items", List.of("a", "b")));
    assertThat(result).isEqualTo("A, B");
  }

  // ---- 'in' operator with list of values ----

  @Test
  void inOperatorWithListLiteral() {
    var tmpl = env.fromString("{{ role in ['admin', 'system'] }}");
    assertThat(tmpl.render(Map.of("role", "system"))).isEqualTo("True");
    assertThat(tmpl.render(Map.of("role", "user"))).isEqualTo("False");
  }
}
