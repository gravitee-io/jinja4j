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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Attribute access, item access, slicing and containment (`in`) behaviour.
 */
class InterpreterAccessTest {

  private final Environment env = new Environment();

  @Nested
  class AttributeAccess {

    @Test
    void undefinedAttributeReturnsEmpty() {
      assertThat(env.fromString("{{ x.foo }}").render()).isEqualTo("");
    }

    @Test
    void nonMapNonStringObjectReturnsUndefined() {
      assertThat(env.fromString("{{ x.foo }}").render(Map.of("x", 42))).isEqualTo("");
    }

    @Test
    void safeStringSupportsStringMethods() {
      env.setAutoEscaping(true);
      assertThat(env.fromString("{{ (text | safe).upper() }}").render(Map.of("text", "hello"))).isEqualTo("HELLO");
    }
  }

  @Nested
  class ItemAccess {

    @Test
    void listNonIntegerKeyReturnsEmpty() {
      assertThat(env.fromString("{{ items['foo'] }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("");
    }

    @Test
    void listOutOfRangeReturnsEmpty() {
      assertThat(env.fromString("{{ items[10] }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("");
      assertThat(env.fromString("{{ items[-10] }}").render(Map.of("items", List.of(1, 2)))).isEqualTo("");
    }

    @Test
    void stringCharAccess() {
      assertThat(env.fromString("{{ text[0] }}").render(Map.of("text", "hello"))).isEqualTo("h");
      assertThat(env.fromString("{{ text[-1] }}").render(Map.of("text", "hello"))).isEqualTo("o");
    }

    @Test
    void stringNonIntegerKeyReturnsEmpty() {
      assertThat(env.fromString("{{ s['foo'] }}").render(Map.of("s", "hello"))).isEqualTo("");
    }

    @Test
    void stringOutOfRangeReturnsEmpty() {
      assertThat(env.fromString("{{ s[100] }}").render(Map.of("s", "hi"))).isEqualTo("");
      assertThat(env.fromString("{{ s[-100] }}").render(Map.of("s", "hi"))).isEqualTo("");
    }

    @Test
    void namespaceItemAccess() {
      assertThat(env.fromString("{% set ns = namespace(x=42) %}{{ ns['x'] }}").render()).isEqualTo("42");
    }

    @Test
    void undefinedItemAccessReturnsEmpty() {
      assertThat(env.fromString("{{ x['foo'] }}").render()).isEqualTo("");
    }

    @Test
    void itemAccessOnUnsupportedTypeReturnsEmpty() {
      assertThat(env.fromString("{{ x[0] }}").render(Map.of("x", 42))).isEqualTo("");
    }
  }

  @Nested
  class Slicing {

    @Test
    void stringSlice() {
      assertThat(env.fromString("{{ text[1:4] }}").render(Map.of("text", "hello"))).isEqualTo("ell");
    }

    @Test
    void stringSliceWithPositiveStep() {
      assertThat(env.fromString("{{ text[::2] }}").render(Map.of("text", "abcde"))).isEqualTo("ace");
    }

    @Test
    void stringSliceReverse() {
      assertThat(env.fromString("{{ text[::-1] }}").render(Map.of("text", "hello"))).isEqualTo("olleh");
      assertThat(env.fromString("{{ s[::-1] }}").render(Map.of("s", "abc"))).isEqualTo("cba");
    }

    @Test
    void stringSliceReverseWithExplicitBounds() {
      assertThat(env.fromString("{{ text[3:0:-1] }}").render(Map.of("text", "abcde"))).isEqualTo("dcb");
    }

    @Test
    void listSliceWithStep() {
      assertThat(env.fromString("{{ items[::2] | join }}").render(Map.of("items", List.of("a", "b", "c", "d")))).isEqualTo(
        "ac"
      );
    }

    @Test
    void listSliceReverse() {
      assertThat(env.fromString("{{ items[::-1] | join(',') }}").render(Map.of("items", List.of(1, 2, 3)))).isEqualTo(
        "3,2,1"
      );
    }

    @Test
    void listSliceWithoutStop() {
      assertThat(env.fromString("{{ items[1:] | join }}").render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("bc");
    }

    @Test
    void listSliceNegativeIndexFromEnd() {
      assertThat(env.fromString("{{ items[-2:] | join }}").render(Map.of("items", List.of("a", "b", "c")))).isEqualTo("bc");
    }

    @Test
    void listSliceReverseWithExplicitBounds() {
      assertThat(
        env.fromString("{{ items[3:1:-1] | join(',') }}").render(Map.of("items", List.of(1, 2, 3, 4, 5)))
      ).isEqualTo("4,3");
    }

    @Test
    void slicingUnsupportedTypeThrows() {
      assertThatThrownBy(() -> env.fromString("{{ x[1:2] }}").render(Map.of("x", 42))).isInstanceOf(TemplateException.class);
    }
  }

  @Nested
  class Membership {

    @Test
    void stringContainsSubstring() {
      assertThat(env.fromString("{{ 'ell' in 'hello' }}").render()).isEqualTo("True");
    }

    @Test
    void listContainsElement() {
      assertThat(env.fromString("{{ 'a' in items }}").render(Map.of("items", List.of("a", "b")))).isEqualTo("True");
    }

    @Test
    void mapContainsKey() {
      assertThat(env.fromString("{{ 'key' in data }}").render(Map.of("data", Map.of("key", 1)))).isEqualTo("True");
      assertThat(env.fromString("{{ 'missing' in data }}").render(Map.of("data", Map.of("key", 1)))).isEqualTo("False");
    }

    @Test
    void mapWithNonStringKeyUsesAsString() {
      assertThat(env.fromString("{{ 42 in data }}").render(Map.of("data", Map.of("42", "val")))).isEqualTo("True");
    }

    @Test
    void safeStringIsSearchable() {
      env.setAutoEscaping(false);
      assertThat(env.fromString("{{ 'world' in (text | safe) }}").render(Map.of("text", "hello world"))).isEqualTo("True");
    }

    @Test
    void unsupportedContainerThrows() {
      assertThatThrownBy(() -> env.fromString("{{ 'a' in 42 }}").render()).isInstanceOf(TemplateException.class);
    }
  }
}
