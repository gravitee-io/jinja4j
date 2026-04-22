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
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Rendering-side behaviour: strict undefined mode, auto-escaping, falsy/truthy rendering.
 */
class InterpreterOutputTest {

  private final Environment env = new Environment();

  @Test
  void strictUndefinedThrowsOnUndefinedVariable() {
    env.setUndefinedBehaviorStrict(true);
    assertThatThrownBy(() -> env.fromString("{{ x }}").render())
      .isInstanceOf(TemplateException.class)
      .hasMessageContaining("Undefined");
  }

  @Test
  void autoEscapingEscapesAngleBrackets() {
    env.setAutoEscaping(true);
    assertThat(env.fromString("{{ text }}").render(Map.of("text", "<b>bold</b>"))).isEqualTo("&lt;b&gt;bold&lt;/b&gt;");
  }

  @Test
  void zeroIntIsFalsyInCondition() {
    assertThat(env.fromString("{% if x %}yes{% else %}no{% endif %}").render(Map.of("x", 0))).isEqualTo("no");
  }

  @Test
  void zeroFloatIsFalsyInCondition() {
    assertThat(env.fromString("{% if x %}yes{% else %}no{% endif %}").render(Map.of("x", 0.0))).isEqualTo("no");
  }

  @Test
  void falseConvertsToZeroThroughIntAndFloatFilters() {
    assertThat(env.fromString("{{ false | int }}").render()).isEqualTo("0");
    assertThat(env.fromString("{{ false | float }}").render()).isEqualTo("0.0");
  }
}
