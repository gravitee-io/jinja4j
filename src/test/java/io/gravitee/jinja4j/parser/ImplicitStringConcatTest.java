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
import io.gravitee.jinja4j.TemplateException;
import org.junit.jupiter.api.Test;

/**
 * Python/Jinja2-style implicit string concatenation: adjacent string literals
 * ({@code "a" "b"}, including across newlines) evaluate as one string.
 */
class ImplicitStringConcatTest {

  private final Environment env = new Environment();

  @Test
  void twoAdjacentLiteralsInOutputExpression() {
    assertThat(env.fromString("{{ \"abc\" \"def\" }}").render()).isEqualTo("abcdef");
  }

  @Test
  void threeLiteralsAcrossNewlinesInsideCallArg() {
    var source = """
      {{- raise_exception(
          "chat_template: tool_calls[].function.arguments must be a "
          "JSON object (mapping), not a string. Deserialize arguments "
          "before passing to the template."
      ) -}}""";
    var template = env.fromString(source); // must parse
    assertThatThrownBy(template::render)
      .isInstanceOf(TemplateException.class)
      .hasMessageContaining(
        "chat_template: tool_calls[].function.arguments must be a JSON object (mapping), not a string. Deserialize arguments before passing to the template."
      );
  }

  @Test
  void adjacentLiteralsInsideListLiteral() {
    assertThat(env.fromString("{{ [\"a\" \"b\", \"c\"] | join('-') }}").render()).isEqualTo("ab-c");
  }

  @Test
  void explicitConcatenationStillWorks() {
    assertThat(env.fromString("{{ \"a\" ~ \"b\" }}").render()).isEqualTo("ab");
    assertThat(env.fromString("{{ \"a\" + \"b\" }}").render()).isEqualTo("ab");
  }

  @Test
  void mixedArgListPassesTwoArgs() {
    var source = "{% macro f(x, y) %}{{ x }}|{{ y }}{% endmacro %}{{ f(\"a\" \"b\", \"c\") }}";
    assertThat(env.fromString(source).render()).isEqualTo("ab|c");
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIf("gemmaTemplateAvailable")
  void gemmaChatTemplateParses() throws Exception {
    var source = java.nio.file.Files.readString(java.nio.file.Path.of(GEMMA_TEMPLATE));
    assertThatCode(() -> env.fromString(source, "gemma_template.jinja")).doesNotThrowAnyException();
  }

  @Test
  void setBlockAssignment() {
    var source = "{%- set greeting -%}Hello {{ name }}!{%- endset -%}{{ greeting }} {{ greeting }}";
    assertThat(env.fromString(source).render(java.util.Map.of("name", "World"))).isEqualTo("Hello World! Hello World!");
  }

  private static final String GEMMA_TEMPLATE = "/tmp/qwen-curl/gemma_template.jinja";

  static boolean gemmaTemplateAvailable() {
    return java.nio.file.Files.exists(java.nio.file.Path.of(GEMMA_TEMPLATE));
  }
}
