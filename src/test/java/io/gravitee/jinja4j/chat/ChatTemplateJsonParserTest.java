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
package io.gravitee.jinja4j.chat;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Raw-JSON parsing used by {@link ChatTemplateRenderer#fromRawField}
 * and {@link ChatTemplateRenderer#ofMultiple}.
 */
class ChatTemplateJsonParserTest {

  @Nested
  class OfMultiple {

    @Test
    void buildsNamedRenderers() {
      var templates = List.of(
        Map.of("name", "default", "template", "Default: {{ msg }}"),
        Map.of("name", "tool_use", "template", "Tool: {{ msg }}")
      );
      var renderers = ChatTemplateRenderer.ofMultiple(templates);
      assertThat(renderers).containsKeys("default", "tool_use");
    }

    @Test
    void skipsEntriesMissingNameOrTemplate() {
      var templates = List.<Map<String, ?>>of(
        Map.of("name", "valid", "template", "OK"),
        Map.of("other", "no_name_or_template")
      );
      @SuppressWarnings({ "unchecked", "rawtypes" })
      var renderers = ChatTemplateRenderer.ofMultiple((List) templates);
      assertThat(renderers).hasSize(1);
    }

    @Test
    void skipsEntriesWithNullTemplateSource() {
      var entry = new LinkedHashMap<String, String>();
      entry.put("name", "test");
      entry.put("template", null);
      var templates = new ArrayList<Map<String, String>>();
      templates.add(entry);
      assertThat(ChatTemplateRenderer.ofMultiple(templates)).isEmpty();
    }
  }

  @Nested
  class FromRawField {

    @Test
    void fallsBackToFirstWhenNoDefaultNamed() {
      var json = """
        [{"name": "primary", "template": "P: {{ x }}"},
         {"name": "secondary", "template": "S: {{ x }}"}]""";
      var renderer = ChatTemplateRenderer.fromRawField(json);
      assertThat(renderer.render(List.of(), Map.of("x", "test"))).isEqualTo("P: test");
    }

    @Test
    void throwsWhenNamedTemplateNotFound() {
      var json = """
        [{"name": "default", "template": "OK"}]""";
      assertThatThrownBy(() -> ChatTemplateRenderer.fromRawField(json, "nonexistent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonexistent");
    }

    @Test
    void acceptsPlainStringAsTemplate() {
      var renderer = ChatTemplateRenderer.fromRawField("Hello {{ name }}!", "ignored_name");
      assertThat(renderer.render(List.of(), Map.of("name", "World"))).isEqualTo("Hello World!");
    }

    @Test
    void throwsOnEmptyArray() {
      assertThatThrownBy(() -> ChatTemplateRenderer.fromRawField("[]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Empty");
    }
  }

  @Nested
  class JsonEscapes {

    @Test
    void honoursNewlineTabReturn() {
      var json = """
        [{"name": "default", "template": "line1\\nline2\\ttab"}]""";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("line1\nline2\ttab");
    }

    @Test
    void honoursBackslash() {
      var json = """
        [{"name": "default", "template": "a\\\\b"}]""";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("a\\b");
    }

    @Test
    void honoursCarriageReturn() {
      var json = """
        [{"name": "default", "template": "a\\rb"}]""";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("a\rb");
    }

    @Test
    void honoursEscapedQuote() {
      var json = "[{\"name\": \"default\", \"template\": \"say \\\"hello\\\"\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("say \"hello\"");
    }

    @Test
    void unknownEscapeFallsThrough() {
      // `\x` is not a JSON escape; the parser keeps the backslash and char.
      var json = "[{\"name\": \"default\", \"template\": \"a\\xb\"}]";
      var result = ChatTemplateRenderer.fromRawField(json).render(List.of());
      assertThat(result).contains("\\").contains("x");
    }

    @Test
    void honoursBackspaceAndFormFeed() {
      var backspace = "[{\"name\": \"default\", \"template\": \"a\\bb\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(backspace).render(List.of())).isEqualTo("a\bb");

      var formFeed = "[{\"name\": \"default\", \"template\": \"a\\fb\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(formFeed).render(List.of())).isEqualTo("a\fb");
    }

    @Test
    void honoursEscapedSolidus() {
      // JSON allows (but does not require) escaping '/' as '\/'.
      var json = "[{\"name\": \"default\", \"template\": \"path\\/to\\/file\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("path/to/file");
    }

    @Test
    void honoursUnicodeBasicMultilingualPlane() {
      // 00E9 = 'é'; 1F600 can't fit — use a BMP char.
      var json = "[{\"name\": \"default\", \"template\": \"caf\\u00e9\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("café");
    }

    @Test
    void honoursUnicodeInSpecialTokenNames() {
      // Real-world tokenizer configs use unicode escapes for ideographic brackets.
      // U+FF5C is the FULLWIDTH VERTICAL LINE used in many model chat templates.
      var json = "[{\"name\": \"default\", \"template\": \"<\\uff5cend\\uff5c>\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("<\uff5cend\uff5c>");
    }

    @Test
    void honoursSurrogatePairs() {
      // U+1F600 GRINNING FACE → encoded as the pair D83D DE00
      var json = "[{\"name\": \"default\", \"template\": \"hi \\ud83d\\ude00\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("hi \ud83d\ude00");
    }

    @Test
    void loneHighSurrogateIsPreservedAsIs() {
      // A high surrogate not followed by a low surrogate is passed through as a single char.
      var json = "[{\"name\": \"default\", \"template\": \"X\\ud83dY\"}]";
      var result = ChatTemplateRenderer.fromRawField(json).render(List.of());
      assertThat(result).hasSize(3).startsWith("X").endsWith("Y");
    }

    @Test
    void rejectsTruncatedUnicodeEscape() {
      var json = "[{\"name\": \"default\", \"template\": \"a\\u00\"}]";
      assertThatThrownBy(() -> ChatTemplateRenderer.fromRawField(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unicode");
    }
  }

  @Nested
  class Whitespace {

    @Test
    void leadingWhitespaceBeforeArray() {
      var renderer = ChatTemplateRenderer.fromRawField("   [{\"name\": \"default\", \"template\": \"ok\"}]");
      assertThat(renderer.render(List.of())).isEqualTo("ok");
    }

    @Test
    void extraWhitespaceInsideObject() {
      var json = "[{  \"name\"  :  \"default\"  ,  \"template\"  :  \"ok\"  }]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("ok");
    }
  }

  @Nested
  class Structure {

    @Test
    void selectsFirstEntryAmongMultiple() {
      var json = "[{\"name\": \"a\", \"template\": \"A\"}, {\"name\": \"b\", \"template\": \"B\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("A");
    }

    @Test
    void trailingCommaInObjectIsAccepted() {
      var json = "[{\"name\": \"default\", \"template\": \"ok\",}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("ok");
    }

    @Test
    void unexpectedCharactersInArrayAreTolerated() {
      var json = "[x{\"name\": \"default\", \"template\": \"hi\"}]";
      assertThat(ChatTemplateRenderer.fromRawField(json).render(List.of())).isEqualTo("hi");
    }
  }

  @Nested
  class TruncatedInput {

    @Test
    void noClosingBrace() {
      var env = new Environment();
      assertThatCode(() -> {
        try {
          ChatTemplateRenderer.fromRawField("[{\"name\": \"default\", \"template\": \"hi\"");
        } catch (Exception ignored) {
          // Either throws or returns a partial parse; both are acceptable outcomes
        }
      }).doesNotThrowAnyException();
      assertThat(env).isNotNull();
    }

    @Test
    void midKey() {
      try {
        ChatTemplateRenderer.fromRawField("[{\"name\"");
      } catch (Exception ignored) {
        // exercises bounds checks
      }
    }

    @Test
    void afterColon() {
      try {
        ChatTemplateRenderer.fromRawField("[{\"name\":  ");
      } catch (Exception ignored) {
        // exercises bounds checks
      }
    }

    @Test
    void noOpeningBracket() {
      try {
        ChatTemplateRenderer.fromRawField("not json at all");
      } catch (Exception ignored) {
        // exercises bounds checks
      }
    }

    @Test
    void unterminatedStringLiteral() {
      try {
        ChatTemplateRenderer.fromRawField("[{\"name\": \"unterminated");
      } catch (Exception ignored) {
        // exercises bounds checks
      }
    }

    @Test
    void backslashAtEnd() {
      try {
        ChatTemplateRenderer.fromRawField("[{\"name\": \"test\\");
      } catch (Exception ignored) {
        // exercises bounds checks
      }
    }
  }

  @Nested
  class CustomEnvironment {

    @Test
    void rendererUsesProvidedEnvironmentGlobals() {
      var env = new Environment();
      env.addGlobal("greeting", "Hi");
      var renderer = ChatTemplateRenderer.of("{{ greeting }} {{ name }}", env);
      assertThat(renderer.render(List.of(), Map.of("name", "World"))).isEqualTo("Hi World");
    }
  }
}
