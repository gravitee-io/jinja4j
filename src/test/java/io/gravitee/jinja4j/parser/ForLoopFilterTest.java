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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Jinja2 loop filters: {% for x in seq if cond %} filters per iteration, and
 * the loop variable reflects the filtered sequence.
 */
class ForLoopFilterTest {

  private final Environment env = new Environment();

  @Test
  void filtersItemsPerIteration() {
    var out = env
      .fromString("{% for n in nums if n % 2 == 0 %}{{ n }} {% endfor %}")
      .render(Map.of("nums", List.of(1, 2, 3, 4, 5, 6)));
    assertThat(out).isEqualTo("2 4 6 ");
  }

  @Test
  void loopVariableReflectsFilteredSequence() {
    var out = env
      .fromString("{% for n in nums if n > 1 %}{{ loop.index }}/{{ loop.length }} {% endfor %}")
      .render(Map.of("nums", List.of(1, 2, 3)));
    assertThat(out).isEqualTo("1/2 2/2 ");
  }

  @Test
  void loopFirstAndLastReflectFilteredSequence() {
    var out = env
      .fromString("{% for n in nums if n != 2 %}{{ loop.first }}-{{ loop.last }} {% endfor %}")
      .render(Map.of("nums", List.of(1, 2, 3)));
    assertThat(out).isEqualTo("True-False False-True ");
  }

  @Test
  void elseBranchRunsWhenFilterRemovesEverything() {
    var out = env
      .fromString("{% for n in nums if n > 10 %}{{ n }}{% else %}empty{% endfor %}")
      .render(Map.of("nums", List.of(1, 2, 3)));
    assertThat(out).isEqualTo("empty");
  }

  @Test
  void filterConditionSeesLoopTargetAttributes() {
    var out = env
      .fromString("{% for m in messages if m.role != 'system' %}{{ m.role }}:{{ m.content }};{% endfor %}")
      .render(
        Map.of(
          "messages",
          List.of(
            Map.of("role", "system", "content", "sys"),
            Map.of("role", "user", "content", "hi"),
            Map.of("role", "assistant", "content", "yo")
          )
        )
      );
    assertThat(out).isEqualTo("user:hi;assistant:yo;");
  }

  @Test
  void filterWithRecursiveKeywordStillParses() {
    var out = env
      .fromString("{% for n in nums if n > 1 recursive %}{{ n }}{% endfor %}")
      .render(Map.of("nums", List.of(1, 2, 3)));
    assertThat(out).isEqualTo("23");
  }

  @Test
  void filterWithTupleUnpacking() {
    var out = env
      .fromString("{% for k, v in items | items if v > 1 %}{{ k }}={{ v }} {% endfor %}")
      .render(Map.of("items", Map.of("a", 1, "b", 2)));
    assertThat(out).isEqualTo("b=2 ");
  }

  @Test
  void plainInlineTernaryStillWorksInOutputExpressions() {
    var out = env.fromString("{{ 'yes' if flag else 'no' }}").render(Map.of("flag", true));
    assertThat(out).isEqualTo("yes");
  }

  @Test
  void glm4ChatTemplateSkipsSystemMessagesInsideLoop() {
    var tpl =
      "{%- for message in messages if message.role != 'system' %}\n" +
      "{{ message.role }}: {{ message.content }}\n" +
      "{%- endfor %}";
    var out = env
      .fromString(tpl)
      .render(
        Map.of(
          "messages",
          List.of(
            Map.of("role", "system", "content", "be nice"),
            Map.of("role", "user", "content", "hello"),
            Map.of("role", "assistant", "content", "hi there")
          )
        )
      );
    assertThat(out).isEqualTo("\nuser: hello\nassistant: hi there");
  }
}
