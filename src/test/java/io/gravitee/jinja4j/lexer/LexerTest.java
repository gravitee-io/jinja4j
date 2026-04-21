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
package io.gravitee.jinja4j.lexer;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.TemplateException;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Lexer behaviour observed through template rendering: literals, operators,
 * numeric syntax, string escapes, comments and error paths.
 */
class LexerTest {

  private final Environment env = new Environment();

  @Nested
  class Literals {

    @Test
    void lowercaseTrueAndFalseKeywords() {
      assertThat(env.fromString("{{ true }}|{{ false }}").render()).isEqualTo("True|False");
    }

    @Test
    void uppercaseTrueAndFalseKeywords() {
      assertThat(env.fromString("{{ True }}|{{ False }}").render()).isEqualTo("True|False");
    }

    @Test
    void identifierContainingUnderscore() {
      assertThat(env.fromString("{{ my_var }}").render(Map.of("my_var", "ok"))).isEqualTo("ok");
    }
  }

  @Nested
  class Numbers {

    @Test
    void floatWithFraction() {
      assertThat(env.fromString("{{ 3.14 }}").render()).isEqualTo("3.14");
    }

    @Test
    void floatWithLowercaseExponent() {
      assertThat(env.fromString("{{ 1e3 }}").render()).isEqualTo("1000.0");
      assertThat(env.fromString("{{ 1.5e2 }}").render()).isEqualTo("150.0");
    }

    @Test
    void floatWithUppercaseExponent() {
      assertThat(env.fromString("{{ 1.5E2 }}").render()).isEqualTo("150.0");
      assertThat(env.fromString("{{ 5E3 }}").render()).isEqualTo("5000.0");
    }

    @Test
    void floatWithSignedExponent() {
      assertThat(env.fromString("{{ 1.5e+2 }}").render()).isEqualTo("150.0");
      assertThat(env.fromString("{{ 1.5e-2 }}").render()).isEqualTo("0.015");
    }

    @Test
    void integerWithSignedExponent() {
      assertThat(env.fromString("{{ 1e+3 }}").render()).isEqualTo("1000.0");
      assertThat(env.fromString("{{ 1e-3 }}").render()).isEqualTo("0.001");
    }

    @Test
    void dotFollowedByNonDigitIsMemberAccess() {
      // `3.foo` → integer 3, then `.foo` dotted access → undefined → default kicks in
      assertThat(env.fromString("{{ n.bar | default(0) }}").render(Map.of("n", 3))).isEqualTo("0");
    }
  }

  @Nested
  class Strings {

    @Test
    void escapeTab() {
      assertThat(env.fromString("{{ 'a\\tb' }}").render()).isEqualTo("a\tb");
    }

    @Test
    void escapeBackslash() {
      assertThat(env.fromString("{{ 'a\\\\b' }}").render()).isEqualTo("a\\b");
    }

    @Test
    void escapeSingleQuote() {
      assertThat(env.fromString("{{ \"a\\'b\" }}").render()).isEqualTo("a'b");
    }

    @Test
    void escapeDoubleQuote() {
      assertThat(env.fromString("{{ 'a\\\"b' }}").render()).isEqualTo("a\"b");
    }

    @Test
    void unknownEscapeIsPassedThrough() {
      assertThat(env.fromString("{{ 'hello\\xworld' }}").render()).isEqualTo("hello\\xworld");
    }
  }

  @Nested
  class Comments {

    @Test
    void commentBlockIsRemoved() {
      assertThat(env.fromString("before{# comment #}after").render()).isEqualTo("beforeafter");
    }
  }

  @Nested
  class Whitespace {

    @Test
    void exprEndWithoutTrimPreservesWhitespace() {
      assertThat(env.fromString("{{ x }}  {{ y }}").render(Map.of("x", "a", "y", "b"))).isEqualTo("a  b");
    }

    @Test
    void stmtEndWithoutTrim() {
      assertThat(env.fromString("{% if true %}yes{% endif %}").render()).isEqualTo("yes");
    }
  }

  @Nested
  class Errors {

    @Test
    void unclosedComment() {
      assertThatThrownBy(() -> env.fromString("{# unclosed comment").render()).isInstanceOf(TemplateException.class);
    }

    @Test
    void unterminatedStringLiteral() {
      assertThatThrownBy(() -> env.fromString("{{ 'unterminated }}").render()).isInstanceOf(TemplateException.class);
    }

    @Test
    void unexpectedCharacter() {
      assertThatThrownBy(() -> env.fromString("{{ @ }}").render()).isInstanceOf(TemplateException.class);
    }

    @Test
    void bangWithoutEquals() {
      assertThatThrownBy(() -> env.fromString("{{ !x }}").render()).isInstanceOf(TemplateException.class);
    }
  }
}
