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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * String, list and dict method behaviour reachable via {@code .methodName()} syntax.
 */
class InterpreterStringMethodsTest {

  private final Environment env = new Environment();

  @Nested
  class StringStripping {

    @Test
    void lstripDefault() {
      assertThat(env.fromString("{{ text.lstrip() }}").render(Map.of("text", "  hello  "))).isEqualTo("hello  ");
    }

    @Test
    void rstripDefault() {
      assertThat(env.fromString("{{ text.rstrip() }}").render(Map.of("text", "  hello  "))).isEqualTo("  hello");
    }

    @Test
    void lstripWithChars() {
      assertThat(env.fromString("{{ text.lstrip('xy') }}").render(Map.of("text", "xxyhello"))).isEqualTo("hello");
    }

    @Test
    void rstripWithChars() {
      assertThat(env.fromString("{{ text.rstrip('!.') }}").render(Map.of("text", "hello!.!"))).isEqualTo("hello");
    }

    @Test
    void stripWithChars() {
      assertThat(env.fromString("{{ text.strip('*') }}").render(Map.of("text", "**hello**"))).isEqualTo("hello");
    }
  }

  @Nested
  class StringQueries {

    @Test
    void findReturnsIndexOrMinusOne() {
      assertThat(env.fromString("{{ text.find('world') }}").render(Map.of("text", "hello world"))).isEqualTo("6");
      assertThat(env.fromString("{{ text.find('missing') }}").render(Map.of("text", "hello"))).isEqualTo("-1");
      assertThat(env.fromString("{{ text.find() }}").render(Map.of("text", "hello"))).isEqualTo("-1");
    }

    @Test
    void count() {
      assertThat(env.fromString("{{ text.count('l') }}").render(Map.of("text", "hello"))).isEqualTo("2");
      assertThat(env.fromString("{{ text.count('x') }}").render(Map.of("text", "hello"))).isEqualTo("0");
      assertThat(env.fromString("{{ text.count() }}").render(Map.of("text", "hello"))).isEqualTo("0");
    }

    @Test
    void startswithAndEndswithMissingArgs() {
      assertThat(env.fromString("{{ text.startswith() }}").render(Map.of("text", "hello"))).isEqualTo("False");
      assertThat(env.fromString("{{ text.endswith() }}").render(Map.of("text", "hello"))).isEqualTo("False");
    }
  }

  @Nested
  class StringTransforms {

    @Test
    void join() {
      assertThat(
        env.fromString("{{ sep.join(items) }}").render(Map.of("sep", ", ", "items", List.of("a", "b", "c")))
      ).isEqualTo("a, b, c");
      assertThat(env.fromString("{{ sep.join() }}").render(Map.of("sep", ", "))).isEqualTo("");
      assertThat(env.fromString("{{ sep.join('not_a_list') }}").render(Map.of("sep", "-"))).isEqualTo("");
    }

    @Test
    void format() {
      assertThat(env.fromString("{{ tmpl.format('World') }}").render(Map.of("tmpl", "Hello {0}!"))).isEqualTo(
        "Hello World!"
      );
      assertThat(env.fromString("{{ tmpl.format(name='World') }}").render(Map.of("tmpl", "Hello {name}!"))).isEqualTo(
        "Hello World!"
      );
    }

    @Test
    void replace() {
      assertThat(env.fromString("{{ text.replace('world', 'there') }}").render(Map.of("text", "hello world"))).isEqualTo(
        "hello there"
      );
    }

    @Test
    void replaceWithTooFewArgsThrows() {
      assertThatThrownBy(() -> env.fromString("{{ 'hello'.replace() }}").render()).isInstanceOf(TemplateException.class);
      assertThatThrownBy(() -> env.fromString("{{ 'hello'.replace('x') }}").render()).isInstanceOf(TemplateException.class);
    }

    @Test
    void upperAndLower() {
      assertThat(env.fromString("{{ text.upper() }}").render(Map.of("text", "hello"))).isEqualTo("HELLO");
      assertThat(env.fromString("{{ text.lower() }}").render(Map.of("text", "HELLO"))).isEqualTo("hello");
    }

    @Test
    void splitWithoutArgsUsesWhitespace() {
      assertThat(env.fromString("{{ '  a  b  c  '.split() | join(',') }}").render()).isEqualTo("a,b,c");
    }

    @Test
    void titleHandlesPunctuationBoundaries() {
      assertThat(env.fromString("{{ text.title() }}").render(Map.of("text", "hello-world foo"))).isEqualTo(
        "Hello-World Foo"
      );
    }

    @Test
    void unknownStringMethodReturnsEmpty() {
      assertThat(env.fromString("{{ text.nonexistent_method }}").render(Map.of("text", "hello"))).isEqualTo("");
    }
  }

  @Nested
  class ListMethods {

    @Test
    void appendReturnsNewList() {
      var tmpl = env.fromString("{% set items = items.append('d') %}{{ items | join(', ') }}");
      assertThat(tmpl.render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("a, b, c, d");
    }

    @Test
    void appendWithoutArgsLeavesListUnchanged() {
      assertThat(
        env.fromString("{% set x = items.append() %}{{ x | join(', ') }}").render(Map.of("items", List.of("a")))
      ).isEqualTo("a");
    }

    @Test
    void lengthAttribute() {
      assertThat(env.fromString("{{ items.length }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("2");
    }

    @Test
    void unknownAttributeReturnsEmpty() {
      assertThat(env.fromString("{{ items.foobar }}").render(Map.of("items", List.of("a")))).isEqualTo("");
    }
  }

  @Nested
  class DictMethods {

    @Test
    void keys() {
      assertThat(
        env.fromString("{{ data.keys() | sort | join(', ') }}").render(Map.of("data", Map.of("b", 2, "a", 1)))
      ).isEqualTo("a, b");
    }

    @Test
    void values() {
      assertThat(
        env.fromString("{{ data.values() | sort | join(', ') }}").render(Map.of("data", Map.of("a", 1, "b", 2)))
      ).isEqualTo("1, 2");
    }

    @Test
    void items() {
      assertThat(
        env.fromString("{% for k, v in data.items() %}{{ k }}:{{ v }} {% endfor %}").render(Map.of("data", Map.of("x", 1)))
      ).contains("x:1");
    }

    @Test
    void updateMergesMaps() {
      var data = new LinkedHashMap<String, Object>();
      data.put("x", 1);
      assertThat(
        env
          .fromString("{% set _ = data.update(extra) %}{{ data.x }},{{ data.y }}")
          .render(Map.of("data", data, "extra", Map.of("y", 2)))
      ).isEqualTo("1,2");
    }

    @Test
    void getWithoutArgsReturnsEmpty() {
      assertThat(env.fromString("{{ data.get() }}").render(Map.of("data", Map.of("a", 1)))).isEqualTo("");
    }

    @Test
    void updateWithoutArgsIsNoOp() {
      assertThat(env.fromString("{% set _ = data.update() %}{{ data.a }}").render(Map.of("data", Map.of("a", 1)))).isEqualTo(
        "1"
      );
    }

    @Test
    void updateWithNonMapArgIsNoOp() {
      assertThat(
        env.fromString("{% set _ = data.update('not_a_map') %}{{ data.a }}").render(Map.of("data", Map.of("a", 1)))
      ).isEqualTo("1");
    }
  }

  @Nested
  class AutoEscaping {

    @Test
    void htmlEscapesKnownCharacters() {
      env.setAutoEscaping(true);
      assertThat(env.fromString("{{ x }}").render(Map.of("x", "a&b<c>d\"e'f"))).isEqualTo("a&amp;b&lt;c&gt;d&#34;e&#39;f");
    }

    @Test
    void escapesBasicAngleBrackets() {
      env.setAutoEscaping(true);
      assertThat(env.fromString("{{ text }}").render(Map.of("text", "<b>bold</b>"))).isEqualTo("&lt;b&gt;bold&lt;/b&gt;");
    }
  }
}
