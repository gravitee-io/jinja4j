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

import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Comprehensive tests for all built-in Jinja2 filters.
 */
class BuiltinFiltersTest {

  private Environment env;

  @BeforeEach
  void setUp() {
    env = new Environment();
  }

  // ---- abs ----

  @Test
  void absInteger() {
    assertThat(env.fromString("{{ (-5) | abs }}").render()).isEqualTo("5");
  }

  @Test
  void absFloat() {
    assertThat(env.fromString("{{ (-3.14) | abs }}").render()).isEqualTo("3.14");
  }

  @Test
  void absPositiveUnchanged() {
    assertThat(env.fromString("{{ 7 | abs }}").render()).isEqualTo("7");
  }

  @Test
  void absOnNonNumberThrows() {
    assertThatThrownBy(() -> env.fromString("{{ 'text' | abs }}").render()).isInstanceOf(TemplateException.class);
  }

  // ---- capitalize ----

  @Test
  void capitalizeWord() {
    assertThat(env.fromString("{{ 'hello' | capitalize }}").render()).isEqualTo("Hello");
  }

  @Test
  void capitalizeEmpty() {
    assertThat(env.fromString("{{ '' | capitalize }}").render()).isEqualTo("");
  }

  @Test
  void capitalizeAlreadyUpper() {
    assertThat(env.fromString("{{ 'HELLO WORLD' | capitalize }}").render()).isEqualTo("Hello world");
  }

  // ---- default / d ----

  @Test
  void defaultUndefined() {
    assertThat(env.fromString("{{ x | default('fallback') }}").render()).isEqualTo("fallback");
  }

  @Test
  void defaultDefined() {
    assertThat(env.fromString("{{ x | default('fallback') }}").render(Map.of("x", "val"))).isEqualTo("val");
  }

  @Test
  void defaultAlias() {
    assertThat(env.fromString("{{ x | d('fallback') }}").render()).isEqualTo("fallback");
  }

  @Test
  void defaultTreatFalsyAsUndefined() {
    assertThat(env.fromString("{{ x | default('fallback', true) }}").render(Map.of("x", ""))).isEqualTo("fallback");
  }

  @Test
  void defaultEmptyStringWithoutFalsy() {
    assertThat(env.fromString("{{ x | default('fallback') }}").render(Map.of("x", ""))).isEqualTo("");
  }

  // ---- dictsort ----

  @Test
  void dictsortByKey() {
    var tmpl = env.fromString("{% for k, v in data | dictsort %}{{ k }}={{ v }} {% endfor %}");
    var data = new LinkedHashMap<String, Object>();
    data.put("c", 3);
    data.put("a", 1);
    data.put("b", 2);
    assertThat(tmpl.render(Map.of("data", data))).isEqualTo("a=1 b=2 c=3 ");
  }

  @Test
  void dictsortOnNonMapThrows() {
    assertThatThrownBy(() -> env.fromString("{{ 'text' | dictsort }}").render()).isInstanceOf(TemplateException.class);
  }

  // ---- first ----

  @Test
  void firstList() {
    assertThat(env.fromString("{{ items | first }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("a");
  }

  @Test
  void firstString() {
    assertThat(env.fromString("{{ 'hello' | first }}").render()).isEqualTo("h");
  }

  @Test
  void firstEmpty() {
    assertThat(env.fromString("{{ items | first }}").render(Map.of("items", List.of()))).isEqualTo("");
  }

  // ---- float ----

  @Test
  void floatFromString() {
    assertThat(env.fromString("{{ '3.14' | float }}").render()).isEqualTo("3.14");
  }

  @Test
  void floatFromInt() {
    assertThat(env.fromString("{{ 42 | float }}").render()).isEqualTo("42.0");
  }

  @Test
  void floatDefault() {
    assertThat(env.fromString("{{ 'abc' | float(9.9) }}").render()).isEqualTo("9.9");
  }

  @Test
  void floatUndefined() {
    assertThat(env.fromString("{{ x | float }}").render()).isEqualTo("0.0");
  }

  // ---- int ----

  @Test
  void intFromString() {
    assertThat(env.fromString("{{ '42' | int }}").render()).isEqualTo("42");
  }

  @Test
  void intFromFloat() {
    assertThat(env.fromString("{{ 3.9 | int }}").render()).isEqualTo("3");
  }

  @Test
  void intDefault() {
    assertThat(env.fromString("{{ 'abc' | int(99) }}").render()).isEqualTo("99");
  }

  @Test
  void intUndefined() {
    assertThat(env.fromString("{{ x | int }}").render()).isEqualTo("0");
  }

  // ---- join ----

  @Test
  void joinWithSeparator() {
    assertThat(env.fromString("{{ items | join(', ') }}").render(Map.of("items", List.of("a", "b", "c")))).isEqualTo(
      "a, b, c"
    );
  }

  @Test
  void joinWithAttribute() {
    var tmpl = env.fromString("{{ items | join(', ', attribute='name') }}");
    var items = List.of(Map.of("name", "Alice"), Map.of("name", "Bob"));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("Alice, Bob");
  }

  @Test
  void joinNoSeparator() {
    assertThat(env.fromString("{{ items | join }}").render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("abc");
  }

  // ---- items ----

  @Test
  void itemsFilter() {
    var tmpl = env.fromString("{% for k, v in data | items %}{{ k }}:{{ v }} {% endfor %}");
    assertThat(tmpl.render(Map.of("data", Map.of("x", 1)))).contains("x:1");
  }

  @Test
  void itemsOnNonMapThrows() {
    assertThatThrownBy(() -> env.fromString("{{ 'text' | items }}").render()).isInstanceOf(TemplateException.class);
  }

  // ---- keys ----

  @Test
  void keysFilter() {
    var tmpl = env.fromString("{{ data | keys | sort | join(', ') }}");
    var data = new LinkedHashMap<String, Object>();
    data.put("b", 2);
    data.put("a", 1);
    assertThat(tmpl.render(Map.of("data", data))).isEqualTo("a, b");
  }

  @Test
  void keysOnNonMapThrows() {
    assertThatThrownBy(() -> env.fromString("{{ 'text' | keys }}").render()).isInstanceOf(TemplateException.class);
  }

  // ---- values ----

  @Test
  void valuesFilter() {
    var tmpl = env.fromString("{{ data | values | sort | join(', ') }}");
    var data = new LinkedHashMap<String, Object>();
    data.put("a", 1);
    data.put("b", 2);
    assertThat(tmpl.render(Map.of("data", data))).isEqualTo("1, 2");
  }

  @Test
  void valuesOnNonMapThrows() {
    assertThatThrownBy(() -> env.fromString("{{ 'text' | values }}").render()).isInstanceOf(TemplateException.class);
  }

  // ---- last ----

  @Test
  void lastList() {
    assertThat(env.fromString("{{ items | last }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("b");
  }

  @Test
  void lastString() {
    assertThat(env.fromString("{{ 'hello' | last }}").render()).isEqualTo("o");
  }

  @Test
  void lastEmpty() {
    assertThat(env.fromString("{{ items | last }}").render(Map.of("items", List.of()))).isEqualTo("");
  }

  // ---- length / count ----

  @Test
  void lengthList() {
    assertThat(env.fromString("{{ items | length }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo("3");
  }

  @Test
  void lengthString() {
    assertThat(env.fromString("{{ 'hello' | length }}").render()).isEqualTo("5");
  }

  @Test
  void lengthMap() {
    assertThat(env.fromString("{{ data | length }}").render(Map.of("data", Map.of("a", 1, "b", 2)))).isEqualTo("2");
  }

  @Test
  void countAlias() {
    assertThat(env.fromString("{{ items | count }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("2");
  }

  // ---- list ----

  @Test
  void listFromString() {
    assertThat(env.fromString("{{ 'abc' | list | join(', ') }}").render()).isEqualTo("a, b, c");
  }

  @Test
  void listFromMap() {
    var tmpl = env.fromString("{{ data | list | sort | join(', ') }}");
    assertThat(tmpl.render(Map.of("data", Map.of("b", 2, "a", 1)))).isEqualTo("a, b");
  }

  @Test
  void listFromList() {
    assertThat(env.fromString("{{ items | list | join(', ') }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("1, 2");
  }

  // ---- lower ----

  @Test
  void lowerFilter() {
    assertThat(env.fromString("{{ 'HELLO World' | lower }}").render()).isEqualTo("hello world");
  }

  // ---- upper ----

  @Test
  void upperFilter() {
    assertThat(env.fromString("{{ 'hello' | upper }}").render()).isEqualTo("HELLO");
  }

  // ---- max ----

  @Test
  void maxFilter() {
    assertThat(env.fromString("{{ items | max }}").render(Map.of("items", List.of(3, 1, 5, 2)))).isEqualTo("5");
  }

  @Test
  void maxEmpty() {
    assertThat(env.fromString("{{ items | max }}").render(Map.of("items", List.of()))).isEqualTo("");
  }

  // ---- min ----

  @Test
  void minFilter() {
    assertThat(env.fromString("{{ items | min }}").render(Map.of("items", List.of(3, 1, 5, 2)))).isEqualTo("1");
  }

  @Test
  void minEmpty() {
    assertThat(env.fromString("{{ items | min }}").render(Map.of("items", List.of()))).isEqualTo("");
  }

  // ---- replace ----

  @Test
  void replaceAll() {
    assertThat(env.fromString("{{ 'aabaa' | replace('a', 'x') }}").render()).isEqualTo("xxbxx");
  }

  @Test
  void replaceWithCount() {
    assertThat(env.fromString("{{ 'aabaa' | replace('a', 'x', 2) }}").render()).isEqualTo("xxbaa");
  }

  @Test
  void replaceTooFewArgsThrows() {
    assertThatThrownBy(() -> env.fromString("{{ 'text' | replace('a') }}").render()).isInstanceOf(TemplateException.class);
  }

  // ---- reverse ----

  @Test
  void reverseString() {
    assertThat(env.fromString("{{ 'hello' | reverse }}").render()).isEqualTo("olleh");
  }

  @Test
  void reverseList() {
    assertThat(
      env.fromString("{{ items | reverse | join(', ') }}").render(Map.of("items", List.of("a", "b", "c")))
    ).isEqualTo("c, b, a");
  }

  // ---- round ----

  @Test
  void roundDefault() {
    assertThat(env.fromString("{{ 3.7 | round }}").render()).isEqualTo("4.0");
  }

  @Test
  void roundWithPrecision() {
    assertThat(env.fromString("{{ 3.14159 | round(2) }}").render()).isEqualTo("3.14");
  }

  @Test
  void roundCeil() {
    assertThat(env.fromString("{{ 3.1 | round(0, method='ceil') }}").render()).isEqualTo("4.0");
  }

  @Test
  void roundFloor() {
    assertThat(env.fromString("{{ 3.9 | round(0, method='floor') }}").render()).isEqualTo("3.0");
  }

  // ---- safe ----

  @Test
  void safeFilter() {
    env.setAutoEscaping(true);
    assertThat(env.fromString("{{ '<b>bold</b>' | safe }}").render()).isEqualTo("<b>bold</b>");
  }

  // ---- select ----

  @Test
  void selectTruthy() {
    assertThat(
      env.fromString("{{ items | select | join(', ') }}").render(Map.of("items", List.of("a", "", "b", "")))
    ).isEqualTo("a, b");
  }

  @Test
  void selectEqualto() {
    assertThat(
      env.fromString("{{ items | select('equalto', 'x') | join }}").render(Map.of("items", List.of("x", "y", "x")))
    ).isEqualTo("xx");
  }

  @Test
  void selectOnNonList() {
    assertThat(env.fromString("{{ 'text' | select }}").render()).isEqualTo("text");
  }

  // ---- selectattr ----

  @Test
  void selectattrTruthy() {
    var tmpl = env.fromString("{{ items | selectattr('active') | map(attribute='name') | join(', ') }}");
    var items = List.of(Map.of("name", "A", "active", true), Map.of("name", "B", "active", false));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("A");
  }

  @Test
  void selectattrEqualto() {
    var tmpl = env.fromString("{{ items | selectattr('type', 'equalto', 'x') | map(attribute='name') | join }}");
    var items = List.of(Map.of("name", "A", "type", "x"), Map.of("name", "B", "type", "y"));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("A");
  }

  @Test
  void selectattrOnNonList() {
    assertThat(env.fromString("{{ 'text' | selectattr('a') }}").render()).isEqualTo("text");
  }

  // ---- reject ----

  @Test
  void rejectTruthy() {
    assertThat(
      env.fromString("{{ items | reject | join(', ') }}").render(Map.of("items", List.of("a", "", "b", "")))
    ).isEqualTo(", ");
  }

  @Test
  void rejectEqualto() {
    assertThat(
      env
        .fromString("{{ items | reject('equalto', 'skip') | join(', ') }}")
        .render(Map.of("items", List.of("a", "skip", "b")))
    ).isEqualTo("a, b");
  }

  @Test
  void rejectOnNonList() {
    assertThat(env.fromString("{{ 'text' | reject }}").render()).isEqualTo("text");
  }

  // ---- rejectattr ----

  @Test
  void rejectattrTruthy() {
    var tmpl = env.fromString("{{ items | rejectattr('hidden') | map(attribute='name') | join(', ') }}");
    var items = List.of(Map.of("name", "A", "hidden", true), Map.of("name", "B", "hidden", false));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("B");
  }

  @Test
  void rejectattrEqualto() {
    var tmpl = env.fromString("{{ items | rejectattr('type', 'equalto', 'hidden') | map(attribute='name') | join }}");
    var items = List.of(Map.of("name", "A", "type", "hidden"), Map.of("name", "B", "type", "visible"));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("B");
  }

  @Test
  void rejectattrOnNonList() {
    assertThat(env.fromString("{{ 'text' | rejectattr('a') }}").render()).isEqualTo("text");
  }

  // ---- sort ----

  @Test
  void sortStrings() {
    assertThat(env.fromString("{{ items | sort | join(', ') }}").render(Map.of("items", List.of("c", "a", "b")))).isEqualTo(
      "a, b, c"
    );
  }

  @Test
  void sortReverse() {
    assertThat(
      env.fromString("{{ items | sort(reverse=true) | join(', ') }}").render(Map.of("items", List.of("a", "b", "c")))
    ).isEqualTo("c, b, a");
  }

  @Test
  void sortByAttribute() {
    var tmpl = env.fromString("{{ items | sort(attribute='age') | map(attribute='name') | join(', ') }}");
    var items = List.of(Map.of("name", "C", "age", 30), Map.of("name", "A", "age", 10), Map.of("name", "B", "age", 20));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("A, B, C");
  }

  @Test
  void sortNumbers() {
    assertThat(env.fromString("{{ items | sort | join(', ') }}").render(Map.of("items", List.of(3, 1, 2)))).isEqualTo(
      "1, 2, 3"
    );
  }

  // ---- string ----

  @Test
  void stringFilter() {
    assertThat(env.fromString("{{ 42 | string }}").render()).isEqualTo("42");
  }

  // ---- title ----

  @Test
  void titleFilter() {
    assertThat(env.fromString("{{ 'hello world' | title }}").render()).isEqualTo("Hello World");
  }

  @Test
  void titleWithPunctuation() {
    assertThat(env.fromString("{{ 'hello-world' | title }}").render()).isEqualTo("Hello-World");
  }

  // ---- trim ----

  @Test
  void trimFilter() {
    assertThat(env.fromString("{{ '  hello  ' | trim }}").render()).isEqualTo("hello");
  }

  // ---- unique ----

  @Test
  void uniqueFilter() {
    assertThat(
      env.fromString("{{ items | unique | join(', ') }}").render(Map.of("items", List.of("a", "b", "a", "c", "b")))
    ).isEqualTo("a, b, c");
  }

  @Test
  void uniquePreservesOrder() {
    assertThat(
      env.fromString("{{ items | unique | join(', ') }}").render(Map.of("items", List.of("c", "a", "c", "b")))
    ).isEqualTo("c, a, b");
  }

  @Test
  void uniqueOnNonList() {
    assertThat(env.fromString("{{ 'text' | unique }}").render()).isEqualTo("text");
  }

  // ---- urlencode ----

  @Test
  void urlencodeSpaces() {
    assertThat(env.fromString("{{ 'hello world' | urlencode }}").render()).isEqualTo("hello%20world");
  }

  @Test
  void urlencodeSpecialChars() {
    assertThat(env.fromString("{{ 'a=b&c=d' | urlencode }}").render()).isEqualTo("a%3Db%26c%3Dd");
  }

  // ---- map ----

  @Test
  void mapAttribute() {
    var tmpl = env.fromString("{{ items | map(attribute='name') | join(', ') }}");
    var items = List.of(Map.of("name", "Alice"), Map.of("name", "Bob"));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("Alice, Bob");
  }

  @Test
  void mapUpper() {
    assertThat(
      env.fromString("{{ items | map('upper') | join(', ') }}").render(Map.of("items", List.of("a", "b")))
    ).isEqualTo("A, B");
  }

  @Test
  void mapLower() {
    assertThat(
      env.fromString("{{ items | map('lower') | join(', ') }}").render(Map.of("items", List.of("A", "B")))
    ).isEqualTo("a, b");
  }

  @Test
  void mapTrim() {
    assertThat(
      env.fromString("{{ items | map('trim') | join(', ') }}").render(Map.of("items", List.of("  a  ", "  b  ")))
    ).isEqualTo("a, b");
  }

  @Test
  void mapString() {
    assertThat(env.fromString("{{ items | map('string') | join(', ') }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
      "1, 2"
    );
  }

  @Test
  void mapInt() {
    assertThat(env.fromString("{{ items | map('int') | join(', ') }}").render(Map.of("items", List.of("1", "2")))).isEqualTo(
      "1, 2"
    );
  }

  @Test
  void mapFloat() {
    assertThat(
      env.fromString("{{ items | map('float') | join(', ') }}").render(Map.of("items", List.of("1.1", "2.2")))
    ).isEqualTo("1.1, 2.2");
  }

  @Test
  void mapCapitalize() {
    assertThat(
      env.fromString("{{ items | map('capitalize') | join(', ') }}").render(Map.of("items", List.of("hello", "world")))
    ).isEqualTo("Hello, World");
  }

  @Test
  void mapUnknownFilterPassthrough() {
    assertThat(
      env.fromString("{{ items | map('nonexistent') | join(', ') }}").render(Map.of("items", List.of("a", "b")))
    ).isEqualTo("a, b");
  }

  @Test
  void mapOnNonList() {
    assertThat(env.fromString("{{ 'text' | map('upper') }}").render()).isEqualTo("text");
  }

  @Test
  void mapNoArgs() {
    assertThat(env.fromString("{{ items | map | join }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("ab");
  }

  // ---- batch ----

  @Test
  void batchEvenSplit() {
    var tmpl = env.fromString("{% for batch in items | batch(2) %}[{{ batch | join(', ') }}]{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c", "d")))).isEqualTo("[a, b][c, d]");
  }

  @Test
  void batchUnevenWithFill() {
    var tmpl = env.fromString("{% for batch in items | batch(3, 'X') %}[{{ batch | join(', ') }}]{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c", "d")))).isEqualTo("[a, b, c][d, X, X]");
  }

  @Test
  void batchUnevenWithoutFill() {
    var tmpl = env.fromString("{% for batch in items | batch(3) %}[{{ batch | join(', ') }}]{% endfor %}");
    assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c", "d")))).isEqualTo("[a, b, c][d]");
  }

  @Test
  void batchOnNonList() {
    assertThat(env.fromString("{{ 'text' | batch(2) }}").render()).isEqualTo("text");
  }

  // ---- tojson ----

  @Test
  void tojsonString() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", "hello"))).isEqualTo("\"hello\"");
  }

  @Test
  void tojsonNumber() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", 42))).isEqualTo("42");
  }

  @Test
  void tojsonBool() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", true))).isEqualTo("true");
  }

  @Test
  void tojsonNull() {
    var ctx = new HashMap<String, Object>();
    ctx.put("val", null);
    assertThat(env.fromString("{{ val | tojson }}").render(ctx)).isEqualTo("null");
  }

  @Test
  void tojsonList() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", List.of(1, 2, 3)))).isEqualTo("[1, 2, 3]");
  }

  @Test
  void tojsonMap() {
    var result = env.fromString("{{ val | tojson }}").render(Map.of("val", Map.of("a", 1)));
    assertThat(result).isEqualTo("{\"a\": 1}");
  }

  @Test
  void tojsonIndented() {
    var result = env.fromString("{{ val | tojson(indent=2) }}").render(Map.of("val", Map.of("a", 1)));
    assertThat(result).contains("\n").contains("  \"a\": 1");
  }

  @Test
  void tojsonSpecialChars() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", "line\nnew"))).isEqualTo("\"line\\nnew\"");
  }

  @Test
  void tojsonNestedStructure() {
    var data = Map.of("list", List.of(Map.of("x", 1)));
    var result = env.fromString("{{ val | tojson(indent=2) }}").render(Map.of("val", data));
    assertThat(result).contains("\"list\"").contains("\"x\": 1");
  }

  @Test
  void tojsonEmptyList() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", List.of()))).isEqualTo("[]");
  }

  @Test
  void tojsonEmptyMap() {
    assertThat(env.fromString("{{ val | tojson }}").render(Map.of("val", Map.of()))).isEqualTo("{}");
  }

  @Test
  void tojsonEmptyListIndented() {
    assertThat(env.fromString("{{ val | tojson(indent=2) }}").render(Map.of("val", List.of()))).isEqualTo("[]");
  }

  @Test
  void tojsonEmptyMapIndented() {
    assertThat(env.fromString("{{ val | tojson(indent=2) }}").render(Map.of("val", Map.of()))).isEqualTo("{}");
  }

  // ---- indent ----

  @Test
  void indentFilter() {
    assertThat(env.fromString("{{ text | indent(4) }}").render(Map.of("text", "line1\nline2\nline3"))).isEqualTo(
      "line1\n    line2\n    line3"
    );
  }

  @Test
  void indentFirstLine() {
    assertThat(env.fromString("{{ text | indent(2, true) }}").render(Map.of("text", "line1\nline2"))).isEqualTo(
      "  line1\n  line2"
    );
  }

  @Test
  void indentDefaultWidth() {
    assertThat(env.fromString("{{ text | indent }}").render(Map.of("text", "a\nb"))).isEqualTo("a\n    b");
  }

  // ---- striptags ----

  @Test
  void striptagsFilter() {
    assertThat(env.fromString("{{ html | striptags }}").render(Map.of("html", "<p>Hello <b>World</b></p>"))).isEqualTo(
      "Hello World"
    );
  }

  @Test
  void striptagsCollapseWhitespace() {
    assertThat(env.fromString("{{ html | striptags }}").render(Map.of("html", "<p>Hello</p>  <p>World</p>"))).isEqualTo(
      "Hello World"
    );
  }

  // ---- wordcount ----

  @Test
  void wordcountFilter() {
    assertThat(env.fromString("{{ text | wordcount }}").render(Map.of("text", "hello world foo"))).isEqualTo("3");
  }

  @Test
  void wordcountEmpty() {
    assertThat(env.fromString("{{ text | wordcount }}").render(Map.of("text", ""))).isEqualTo("0");
  }

  @Test
  void wordcountSingleWord() {
    assertThat(env.fromString("{{ text | wordcount }}").render(Map.of("text", "hello"))).isEqualTo("1");
  }

  // ---- truncate ----

  @Test
  void truncateFilter() {
    assertThat(
      env.fromString("{{ text | truncate(10) }}").render(Map.of("text", "Hello World, this is a long text"))
    ).isEqualTo("Hello W...");
  }

  @Test
  void truncateCustomEnd() {
    assertThat(
      env.fromString("{{ text | truncate(10, '>>') }}").render(Map.of("text", "Hello World, this is long"))
    ).isEqualTo("Hello Wo>>");
  }

  @Test
  void truncateShortStringUnchanged() {
    assertThat(env.fromString("{{ text | truncate(100) }}").render(Map.of("text", "short"))).isEqualTo("short");
  }

  @Test
  void truncateDefaultLength() {
    var longText = "a".repeat(300);
    var result = env.fromString("{{ text | truncate }}").render(Map.of("text", longText));
    assertThat(result).hasSize(255);
    assertThat(result).endsWith("...");
  }

  // ---- attr ----

  @Test
  void attrFilter() {
    assertThat(env.fromString("{{ data | attr('key') }}").render(Map.of("data", Map.of("key", "value")))).isEqualTo("value");
  }

  @Test
  void attrFilterMissing() {
    assertThat(env.fromString("{{ data | attr('missing') }}").render(Map.of("data", Map.of("key", "value")))).isEqualTo("");
  }

  @Test
  void attrNoArgsThrows() {
    assertThatThrownBy(() -> env.fromString("{{ data | attr }}").render(Map.of("data", Map.of("a", 1)))).isInstanceOf(
      TemplateException.class
    );
  }

  @Test
  void attrOnNonMap() {
    assertThat(env.fromString("{{ 'text' | attr('x') }}").render()).isEqualTo("");
  }

  // ---- e / escape / forceescape ----

  @Test
  void escapeFilter() {
    assertThat(env.fromString("{{ text | escape }}").render(Map.of("text", "<b>\"hello\" & 'world'</b>"))).isEqualTo(
      "&lt;b&gt;&#34;hello&#34; &amp; &#39;world&#39;&lt;/b&gt;"
    );
  }

  @Test
  void eAlias() {
    assertThat(env.fromString("{{ text | e }}").render(Map.of("text", "<br>"))).isEqualTo("&lt;br&gt;");
  }

  @Test
  void forceescapeFilter() {
    assertThat(env.fromString("{{ text | forceescape }}").render(Map.of("text", "<div>"))).isEqualTo("&lt;div&gt;");
  }

  // ---- xmlattr ----

  @Test
  void xmlattrFilter() {
    var tmpl = env.fromString("<div {{ attrs | xmlattr }}>");
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("class", "main");
    attrs.put("id", "content");
    assertThat(tmpl.render(Map.of("attrs", attrs))).isEqualTo("<div class=\"main\" id=\"content\">");
  }

  @Test
  void xmlattrSkipsNullAndUndefined() {
    var tmpl = env.fromString("<div {{ attrs | xmlattr }}>");
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("class", "main");
    attrs.put("id", null);
    assertThat(tmpl.render(Map.of("attrs", attrs))).isEqualTo("<div class=\"main\">");
  }

  @Test
  void xmlattrEscapesValues() {
    var tmpl = env.fromString("<div {{ attrs | xmlattr }}>");
    assertThat(tmpl.render(Map.of("attrs", Map.of("title", "a\"b<c>d&e")))).isEqualTo(
      "<div title=\"a&#34;b&lt;c&gt;d&amp;e\">"
    );
  }

  @Test
  void xmlattrOnNonMap() {
    assertThat(env.fromString("{{ 'text' | xmlattr }}").render()).isEqualTo("text");
  }

  // ---- groupby ----

  @Test
  void groupbyFilter() {
    var tmpl = env.fromString(
      "{% for g in items | groupby('category') %}{{ g.grouper }}:{% for i in g.list %}{{ i.name }}{% endfor %} {% endfor %}"
    );
    var items = List.of(
      Map.of("name", "A", "category", "x"),
      Map.of("name", "B", "category", "y"),
      Map.of("name", "C", "category", "x")
    );
    var result = tmpl.render(Map.of("items", items));
    assertThat(result).contains("x:AC").contains("y:B");
  }

  @Test
  void groupbyOnNonList() {
    assertThat(env.fromString("{{ 'text' | groupby('x') }}").render()).isEqualTo("text");
  }

  // ---- sum ----

  @Test
  void sumIntegers() {
    assertThat(env.fromString("{{ items | sum }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo("6");
  }

  @Test
  void sumFloats() {
    assertThat(env.fromString("{{ items | sum }}").render(Map.of("items", List.of(1.5, 2.5)))).isEqualTo("4.0");
  }

  @Test
  void sumWithStart() {
    assertThat(env.fromString("{{ items | sum(10) }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("13.0");
  }

  @Test
  void sumWithAttribute() {
    var tmpl = env.fromString("{{ items | sum(attribute='val') }}");
    var items = List.of(Map.of("val", 10), Map.of("val", 20));
    assertThat(tmpl.render(Map.of("items", items))).isEqualTo("30");
  }

  @Test
  void sumEmpty() {
    assertThat(env.fromString("{{ items | sum }}").render(Map.of("items", List.of()))).isEqualTo("0");
  }

  @Test
  void sumOnNonList() {
    assertThat(env.fromString("{{ 'text' | sum }}").render()).isEqualTo("0");
  }

  // ---- center ----

  @Test
  void centerFilter() {
    assertThat(env.fromString("{{ 'hi' | center(10) }}").render()).isEqualTo("    hi    ");
  }

  @Test
  void centerAlreadyWide() {
    assertThat(env.fromString("{{ 'hello world' | center(5) }}").render()).isEqualTo("hello world");
  }

  @Test
  void centerDefaultWidth() {
    var result = env.fromString("{{ 'hi' | center }}").render();
    assertThat(result).hasSize(80);
    assertThat(result.strip()).isEqualTo("hi");
  }

  // ---- filesizeformat ----

  @Test
  void filesizeformatBytes() {
    assertThat(env.fromString("{{ 100 | filesizeformat }}").render()).isEqualTo("100 Bytes");
  }

  @Test
  void filesizeformatKB() {
    assertThat(env.fromString("{{ 1500 | filesizeformat }}").render()).isEqualTo("1.5 kB");
  }

  @Test
  void filesizeformatMB() {
    assertThat(env.fromString("{{ 1500000 | filesizeformat }}").render()).isEqualTo("1.5 MB");
  }

  @Test
  void filesizeformatBinary() {
    assertThat(env.fromString("{{ 1048576 | filesizeformat(true) }}").render()).isEqualTo("1.0 MiB");
  }

  // ---- Chained filter combinations ----

  @Test
  void chainTrimUpperJoin() {
    assertThat(
      env
        .fromString("{{ items | map('trim') | map('upper') | join(', ') }}")
        .render(Map.of("items", List.of("  a  ", "  b  ")))
    ).isEqualTo("A, B");
  }

  @Test
  void chainSelectJoin() {
    assertThat(
      env.fromString("{{ items | select | map('upper') | join(', ') }}").render(Map.of("items", List.of("a", "", "b")))
    ).isEqualTo("A, B");
  }

  @Test
  void chainSortReverseJoin() {
    assertThat(
      env.fromString("{{ items | sort | reverse | join(', ') }}").render(Map.of("items", List.of("a", "c", "b")))
    ).isEqualTo("c, b, a");
  }

  @Test
  void chainUniqueSort() {
    assertThat(
      env.fromString("{{ items | unique | sort | join(', ') }}").render(Map.of("items", List.of("c", "a", "c", "b")))
    ).isEqualTo("a, b, c");
  }

  // ---- Filter-specific edge cases ----
  // Extracted from former CoverageGapTest / BranchCoverageTest to keep filter
  // behaviour co-located with the rest of the filter tests.

  @Test
  void mapIntOnUnparseableFallsBackToZero() {
    assertThat(
      env.fromString("{{ items | map('int') | join(',') }}").render(Map.of("items", List.of("abc", "def")))
    ).isEqualTo("0,0");
  }

  @Test
  void mapFloatOnUnparseableFallsBackToZero() {
    assertThat(
      env.fromString("{{ items | map('float') | join(',') }}").render(Map.of("items", List.of("abc", "x")))
    ).isEqualTo("0.0,0.0");
  }

  @Test
  void mapCapitalizeLeavesEmptyItemUntouched() {
    assertThat(
      env.fromString("{{ items | map('capitalize') | join('-') }}").render(Map.of("items", List.of("", "hi")))
    ).isEqualTo("-Hi");
  }

  @Test
  void mapUnknownFilterPassesThrough() {
    assertThat(env.fromString("{{ items | map('nope') | join(',') }}").render(Map.of("items", List.of(1, 2)))).isEqualTo(
      "1,2"
    );
  }

  @Test
  void mapAttributeReturnsUndefinedForNonMapItems() {
    assertThat(
      env.fromString("{{ items | map(attribute='x') | join }}").render(Map.of("items", List.of("a", "b")))
    ).isEqualTo("");
  }

  @Test
  void sortReverseFalse() {
    assertThat(
      env.fromString("{{ items | sort(reverse=false) | join(',') }}").render(Map.of("items", List.of(3, 1, 2)))
    ).isEqualTo("1,2,3");
  }

  @Test
  void sortAttributeOnMixedList() {
    var items = new java.util.ArrayList<>();
    items.add(Map.of("k", 1));
    items.add("not-a-map");
    items.add(Map.of("k", 2));
    assertThat(env.fromString("{{ items | sort(attribute='k') | length }}").render(Map.of("items", items))).isEqualTo("3");
  }

  @Test
  void sortMixedNumberAndStringFallsBackToString() {
    var items = new java.util.ArrayList<>();
    items.add(5);
    items.add("abc");
    items.add(3);
    assertThat(env.fromString("{{ items | sort | length }}").render(Map.of("items", items))).isEqualTo("3");
  }

  @Test
  void sortOnNonListReturnsValue() {
    assertThat(env.fromString("{{ x | sort }}").render(Map.of("x", "hello"))).isEqualTo("hello");
  }

  @Test
  void intFilterOnFloatTruncates() {
    assertThat(env.fromString("{{ 3.9 | int }}").render()).isEqualTo("3");
  }

  @Test
  void intFilterEmptyStringReturnsZero() {
    assertThat(env.fromString("{{ '' | int }}").render()).isEqualTo("0");
  }

  @Test
  void intFilterEmptyStringWithDefault() {
    assertThat(env.fromString("{{ '' | int(99) }}").render()).isEqualTo("99");
  }

  @Test
  void intFilterFallsBackOnUnparseableString() {
    assertThat(env.fromString("{{ 'nope' | int }}").render()).isEqualTo("0");
  }

  @Test
  void groupbyWithoutAttributeReturnsList() {
    assertThat(env.fromString("{{ items | groupby | length }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("2");
  }

  @Test
  void groupbyWithNonMapItemsBucketsThemUnderEmptyKey() {
    var items = new java.util.ArrayList<>();
    items.add("not-a-map");
    items.add(Map.of("category", "x", "name", "Alice"));
    assertThat(env.fromString("{{ (items | groupby('category')) | length }}").render(Map.of("items", items))).isEqualTo("2");
  }

  @Test
  void groupbyMapMissingAttributeFallsIntoEmptyKey() {
    var items = List.of(Map.of("other", 1), Map.of("category", "x"));
    assertThat(env.fromString("{{ (items | groupby('category')) | length }}").render(Map.of("items", items))).isEqualTo("2");
  }

  @Test
  void defaultWithoutArgUsesEmptyString() {
    assertThat(env.fromString("{{ x | default }}").render()).isEqualTo("");
  }

  @Test
  void defaultWithFalsyFlagAndFalseValuePassesThroughEmptyString() {
    assertThat(env.fromString("{{ '' | default('fallback', false) }}").render()).isEqualTo("");
  }

  @Test
  void defaultReturnsValueWhenTruthyEvenWithFalsyFlag() {
    assertThat(env.fromString("{{ 'present' | default('fallback', true) }}").render()).isEqualTo("present");
  }

  @Test
  void lengthOnNonCollectionReturnsZero() {
    assertThat(env.fromString("{{ x | length }}").render(Map.of("x", 42))).isEqualTo("0");
  }

  @Test
  void lengthOfSafeString() {
    env.setAutoEscaping(true);
    assertThat(env.fromString("{{ ('hello' | safe) | length }}").render()).isEqualTo("5");
  }

  @Test
  void joinOnNonListReturnsValue() {
    assertThat(env.fromString("{{ x | join(', ') }}").render(Map.of("x", "hello"))).isEqualTo("hello");
  }

  @Test
  void reverseOnNonListNonStringReturnsValueAsString() {
    assertThat(env.fromString("{{ x | reverse }}").render(Map.of("x", 42))).isEqualTo("42");
  }

  @Test
  void listFilterOnSingleValueWrapsInList() {
    assertThat(env.fromString("{{ x | list | join(', ') }}").render(Map.of("x", 42))).isEqualTo("42");
  }

  // ---- BuiltinFilters façade ----

  @Test
  void facadeExposesHas() {
    assertThat(io.gravitee.jinja4j.filter.BuiltinFilters.has("upper")).isTrue();
    assertThat(io.gravitee.jinja4j.filter.BuiltinFilters.has("nonexistent")).isFalse();
  }
}
