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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code {% call macro(args) %}body{% endcall %}}, where the body
 * is invoked via {@code {{ caller() }}} inside the target macro.
 */
class InterpreterCallBlockTest {

  private final Environment env = new Environment();

  @Test
  @DisplayName("caller() inside the macro renders the block body")
  void callerRendersBlockBody() {
    var tmpl = """
      {%- macro render_dialog(title) -%}
      <div class="dialog"><h1>{{ title }}</h1><div class="body">{{ caller() }}</div></div>
      {%- endmacro -%}
      {%- call render_dialog("Hello") -%}
      <p>This is the dialog body.</p>
      {%- endcall -%}
      """;
    assertThat(env.fromString(tmpl).render()).isEqualTo(
      "<div class=\"dialog\"><h1>Hello</h1><div class=\"body\"><p>This is the dialog body.</p></div></div>"
    );
  }

  @Test
  @DisplayName("Block body can reference outer-scope variables")
  void blockBodyCanReferenceOuterScope() {
    var tmpl = """
      {%- macro wrap() -%}
      [{{ caller() }}]
      {%- endmacro -%}
      {%- call wrap() -%}
      Hello, {{ name }}!
      {%- endcall -%}
      """;
    assertThat(env.fromString(tmpl).render(Map.of("name", "Remi"))).isEqualTo("[Hello, Remi!]");
  }

  @Test
  @DisplayName("Macros that do not invoke caller() simply drop the block body")
  void macroWithoutCallerDropsBody() {
    var tmpl = """
      {%- macro plain() -%}
      just the macro
      {%- endmacro -%}
      {%- call plain() -%}
      this body is ignored
      {%- endcall -%}
      """;
    assertThat(env.fromString(tmpl).render()).isEqualTo("just the macro");
  }

  @Test
  @DisplayName("Call block can pass positional arguments to the macro")
  void callBlockPassesArgumentsThrough() {
    var tmpl = """
      {%- macro card(title, subtitle) -%}
      <card title="{{ title }}" subtitle="{{ subtitle }}">{{ caller() }}</card>
      {%- endmacro -%}
      {%- call card("A", "B") -%}body{%- endcall -%}
      """;
    assertThat(env.fromString(tmpl).render()).isEqualTo("<card title=\"A\" subtitle=\"B\">body</card>");
  }

  @Test
  @DisplayName("Nested call blocks bind the innermost caller")
  void nestedCallBlocksShadowCaller() {
    var tmpl = """
      {%- macro outer() -%}
      OUTER({{ caller() }})
      {%- endmacro -%}
      {%- macro inner() -%}
      INNER({{ caller() }})
      {%- endmacro -%}
      {%- call outer() -%}
      {%- call inner() -%}body{%- endcall -%}
      {%- endcall -%}
      """;
    assertThat(env.fromString(tmpl).render()).isEqualTo("OUTER(INNER(body))");
  }

  @Test
  @DisplayName("Call block body is only evaluated when caller() is invoked")
  void callerIsEvaluatedLazily() {
    // The macro decides whether to call caller() at all. If it calls it
    // twice, the body renders twice.
    var tmpl = """
      {%- macro repeat() -%}
      {{ caller() }}-{{ caller() }}
      {%- endmacro -%}
      {%- call repeat() -%}X{%- endcall -%}
      """;
    assertThat(env.fromString(tmpl).render()).isEqualTo("X-X");
  }

  @Test
  @DisplayName("caller outside a call block is undefined (non-strict mode)")
  void callerIsUndefinedOutsideCallBlock() {
    // Without a surrounding {% call %}, the name 'caller' is not bound.
    // Non-strict mode renders undefined as empty string.
    assertThat(env.fromString("[{{ caller() if caller is defined else 'nope' }}]").render()).isEqualTo("[nope]");
  }
}
