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
package io.gravitee.jinja4j.security;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.chat.ChatTemplateRenderer;
import io.gravitee.jinja4j.chat.Message;
import io.gravitee.jinja4j.value.SafeString;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regression tests freezing the safety contract: user-supplied data
 * passed into the render context is treated as opaque data and is
 * <strong>never</strong> re-parsed as Jinja syntax.
 *
 * <p>The template source is parsed exactly once, at
 * {@link Environment#fromString(String)} time. At render time, context
 * values flow through {@code ValueConverter} into {@code Value.*}
 * records and are appended to the output buffer verbatim by the
 * interpreter. There is no second lexer/parser pass over variable
 * contents.</p>
 *
 * <p>These tests lock that behaviour down so any future refactor that
 * accidentally introduces recursive evaluation of variable values
 * will fail CI immediately.</p>
 */
class UserDataIsNotReparsedTest {

  @Nested
  @DisplayName("Core engine: Environment.fromString(...).render(Map)")
  class CoreEngine {

    private final Environment env = new Environment();

    @Test
    @DisplayName("{{ expr }} inside a variable value is not evaluated")
    void variableContainingOutputDirectiveIsNotEvaluated() {
      var tmpl = env.fromString("{{ name }}");
      assertThat(tmpl.render(Map.of("name", "{{ 7*7 }}"))).isEqualTo("{{ 7*7 }}");
    }

    @Test
    @DisplayName("{% for %} inside a variable value is not expanded into a loop")
    void variableContainingForLoopIsNotExpanded() {
      var tmpl = env.fromString("{{ x }}");
      var payload = "{% for i in range(10) %}BOOM{% endfor %}";
      assertThat(tmpl.render(Map.of("x", payload))).isEqualTo(payload);
    }

    @Test
    @DisplayName("{# comment #} inside a variable value is not stripped")
    void variableContainingCommentIsNotStripped() {
      var tmpl = env.fromString("{{ x }}");
      assertThat(tmpl.render(Map.of("x", "{# comment #}visible"))).isEqualTo("{# comment #}visible");
    }

    @Test
    @DisplayName("raise_exception() smuggled via a variable does not throw")
    void variableContainingRaiseExceptionDoesNotThrow() {
      var tmpl = env.fromString("{{ x }}");
      var payload = "{{ raise_exception('pwn') }}";
      assertThatCode(() -> tmpl.render(Map.of("x", payload))).doesNotThrowAnyException();
      assertThat(tmpl.render(Map.of("x", payload))).isEqualTo(payload);
    }

    @Test
    @DisplayName("range(10**9) smuggled via a variable does not allocate")
    void variableContainingLargeRangeDoesNotAllocate() {
      var tmpl = env.fromString("{{ x }}");
      var payload = "{{ range(10**9) | list | length }}";
      assertThat(tmpl.render(Map.of("x", payload))).isEqualTo(payload);
    }

    @Test
    @DisplayName("Inner {{ var }} reference inside data is not resolved against the context")
    void innerDirectiveDoesNotResolveAgainstSiblingContext() {
      var tmpl = env.fromString("{{ x }}");
      var ctx = Map.<String, Object>of("x", "prefix {{ secret }} suffix", "secret", "TOP_SECRET");
      assertThat(tmpl.render(ctx)).isEqualTo("prefix {{ secret }} suffix").doesNotContain("TOP_SECRET");
    }

    @Test
    @DisplayName("All three delimiter pairs in the same value render verbatim")
    void mixedDelimitersRenderVerbatim() {
      var tmpl = env.fromString("{{ x }}");
      var payload = "{{ a }} {% b %} {# c #}";
      assertThat(tmpl.render(Map.of("x", payload))).isEqualTo(payload);
    }

    @Test
    @DisplayName("Filters do not re-parse their string input")
    void filtersDoNotReparseStringInput() {
      var tmpl = env.fromString("{{ x | upper }}");
      // Only letters are uppercased; braces/pipes/stars are untouched,
      // and the whole value is still a literal string, not code.
      assertThat(tmpl.render(Map.of("x", "{{ 7*7 }}"))).isEqualTo("{{ 7*7 }}");
    }

    @Test
    @DisplayName("Values iterated by {% for %} in the template are not re-parsed")
    void listItemsContainingDirectivesAreNotReparsed() {
      var tmpl = env.fromString("{% for m in items %}[{{ m }}]{% endfor %}");
      var items = List.of("{{ a }}", "{% b %}", "{# c #}");
      assertThat(tmpl.render(Map.of("items", items))).isEqualTo("[{{ a }}][{% b %}][{# c #}]");
    }

    @Test
    @DisplayName("A variable containing {% endif %} cannot break the enclosing conditional")
    void variableContainingEndifDoesNotBreakConditional() {
      var tmpl = env.fromString("{% if flag %}<{{ x }}>{% endif %}AFTER");
      var payload = "{% endif %}LEAK";
      var ctx = Map.<String, Object>of("flag", true, "x", payload);
      assertThat(tmpl.render(ctx)).isEqualTo("<{% endif %}LEAK>AFTER");
    }
  }

  @Nested
  @DisplayName("Chat path: ChatTemplateRenderer.render(messages, extraVars)")
  class ChatPath {

    private static final String CHAT_TEMPLATE =
      "{% for msg in messages %}<|im_start|>{{ msg['role'] }}\n{{ msg['content'] }}<|im_end|>\n{% endfor %}";

    @Test
    @DisplayName("Message content containing {{ 7*7 }} is not evaluated")
    void messageContentOutputDirectiveIsNotEvaluated() {
      var renderer = ChatTemplateRenderer.of(CHAT_TEMPLATE);
      var out = renderer.render(List.of(new Message("user", "{{ 7*7 }}")), Map.of());
      assertThat(out).contains("{{ 7*7 }}").doesNotContain("49");
    }

    @Test
    @DisplayName("Message content containing {% for %} is not expanded")
    void messageContentForLoopIsNotExpanded() {
      var renderer = ChatTemplateRenderer.of(CHAT_TEMPLATE);
      var payload = "{% for i in range(10) %}x{% endfor %}";
      var out = renderer.render(List.of(new Message("user", payload)), Map.of());
      assertThat(out).contains(payload);
      // If the payload had been expanded we would see ten x's in a row
      // *without* the surrounding {% for %}/{% endfor %} markers.
      assertThat(out).doesNotContain("xxxxxxxxxx<|im_end|>");
    }

    @Test
    @DisplayName("extraVars values containing Jinja syntax are not re-parsed")
    void extraVarsContainingDirectivesAreNotReparsed() {
      var renderer = ChatTemplateRenderer.of("{{ injected }}");
      var out = renderer.render(List.of(), Map.of("injected", "{{ bad }}"));
      assertThat(out).isEqualTo("{{ bad }}");
    }

    @Test
    @DisplayName("Message content containing {% endfor %} does not break the outer loop")
    void messageContentEndforDoesNotBreakOuterLoop() {
      var renderer = ChatTemplateRenderer.of(CHAT_TEMPLATE);
      var messages = List.of(
        new Message("user", "{% endfor %}{% for i in range(1000000000) %}"),
        new Message("assistant", "second turn")
      );
      var out = renderer.render(messages, Map.of());
      // Both turns must appear with their scaffolding intact.
      assertThat(out)
        .contains("<|im_start|>user\n{% endfor %}{% for i in range(1000000000) %}<|im_end|>")
        .contains("<|im_start|>assistant\nsecond turn<|im_end|>");
    }
  }

  @Nested
  @DisplayName("SafeString wrapper: content is never re-parsed as Jinja")
  class SafeStringScope {

    @Test
    @DisplayName("SafeString-wrapped value containing Jinja syntax is not evaluated")
    void safeStringDoesNotTriggerReparse() {
      var env = new Environment();
      var tmpl = env.fromString("{{ x }}");
      var out = tmpl.render(Map.of("x", new SafeString("{{ 7*7 }}")));
      // The critical guarantee: the literal payload is preserved and
      // the expression "7*7" is never evaluated to "49".
      assertThat(out).contains("{{ 7*7 }}").doesNotContain("49");
    }

    @Test
    @DisplayName("SafeString-wrapped value is also inert under auto-escape")
    void safeStringUnderAutoEscapeStillInert() {
      var env = new Environment();
      env.setAutoEscaping(true);
      var tmpl = env.fromString("{{ x }}");
      // Auto-escape is about HTML escaping, not about re-parsing: the
      // {{ 7*7 }} substring must not be evaluated.
      var out = tmpl.render(Map.of("x", new SafeString("{{ 7*7 }}")));
      assertThat(out).contains("{{ 7*7 }}").doesNotContain("49");
    }
  }
}
