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
import io.gravitee.jinja4j.value.Namespace;
import io.gravitee.jinja4j.value.Value;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Loops, iteration, unpacking, set, with, include, and macro invocations.
 */
class InterpreterControlFlowTest {

  private final Environment env = new Environment();

  @Nested
  class ForLoops {

    @Test
    void loopRevindex() {
      assertThat(
        env.fromString("{% for x in items %}{{ loop.revindex }}{% endfor %}").render(Map.of("items", List.of("a", "b", "c")))
      ).isEqualTo("321");
    }

    @Test
    void loopRevindex0() {
      assertThat(
        env
          .fromString("{% for x in items %}{{ loop.revindex0 }}{% endfor %}")
          .render(Map.of("items", List.of("a", "b", "c")))
      ).isEqualTo("210");
    }

    @Test
    void loopCycleAlternates() {
      assertThat(
        env
          .fromString("{% for x in items %}{{ loop.cycle('odd', 'even') }}{% endfor %}")
          .render(Map.of("items", List.of(1, 2, 3)))
      ).isEqualTo("oddevenodd");
    }

    @Test
    void loopCycleWithoutArgsReturnsEmpty() {
      assertThat(
        env.fromString("{% for x in items %}{{ loop.cycle() }}{% endfor %}").render(Map.of("items", List.of(1, 2)))
      ).isEqualTo("");
    }
  }

  @Nested
  class Iteration {

    @Test
    void iterateOverStringYieldsCharacters() {
      assertThat(env.fromString("{% for c in text %}{{ c }}-{% endfor %}").render(Map.of("text", "abc"))).isEqualTo(
        "a-b-c-"
      );
    }

    @Test
    void iterateOverMapYieldsKeys() {
      var data = new LinkedHashMap<String, Object>();
      data.put("a", 1);
      data.put("b", 2);
      assertThat(env.fromString("{% for k in data %}{{ k }}{% endfor %}").render(Map.of("data", data))).isEqualTo("ab");
    }

    @Test
    void iterateOverUndefinedRunsElseBranch() {
      assertThat(env.fromString("{% for x in items %}{{ x }}{% else %}empty{% endfor %}").render()).isEqualTo("empty");
    }

    @Test
    void iterateOverNullRunsElseBranch() {
      var ctx = new HashMap<String, Object>();
      ctx.put("items", null);
      assertThat(env.fromString("{% for x in items %}{{ x }}{% else %}empty{% endfor %}").render(ctx)).isEqualTo("empty");
    }

    @Test
    void iterateOverNonIterableThrows() {
      assertThatThrownBy(() -> env.fromString("{% for x in data %}{% endfor %}").render(Map.of("data", 42))).isInstanceOf(
        TemplateException.class
      );
    }
  }

  @Nested
  class Unpacking {

    @Test
    void unpackListInForLoop() {
      var tmpl = env.fromString("{% for k, v in items %}{{ k }}-{{ v }} {% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of(List.of("a", 1), List.of("b", 2))))).isEqualTo("a-1 b-2 ");
    }

    @Test
    void unpackFewerItemsThanTargets() {
      var tmpl = env.fromString("{% for a, b, c in items %}{{ a }}-{{ b }}-{{ c }}{% endfor %}");
      assertThat(tmpl.render(Map.of("items", List.of(List.of("x", "y"))))).isEqualTo("x-y-");
    }

    @Test
    void unpackNonIterableThrows() {
      assertThatThrownBy(() ->
        env.fromString("{% for a, b in items %}{% endfor %}").render(Map.of("items", List.of(42)))
      ).isInstanceOf(TemplateException.class);
    }

    @Test
    void unpackDictItemsReturnsKeyValuePairs() {
      var data = new LinkedHashMap<String, Object>();
      data.put("x", 1);
      data.put("y", 2);
      var tmpl = env.fromString("{% for k, v in data | items %}{{ k }}={{ v }} {% endfor %}");
      assertThat(tmpl.render(Map.of("data", data))).contains("x=1").contains("y=2");
    }
  }

  @Nested
  class Macros {

    @Test
    void nonCallableValueThrows() {
      assertThatThrownBy(() -> env.fromString("{{ x() }}").render(Map.of("x", 42))).isInstanceOf(TemplateException.class);
    }
  }

  @Nested
  class Namespaces {

    @Test
    void setAttrOnNonNamespaceThrows() {
      assertThatThrownBy(() -> env.fromString("{% set x = 42 %}{% set x.foo = 1 %}").render()).isInstanceOf(
        TemplateException.class
      );
    }

    @Test
    void namespaceInContextIsUsable() {
      var ctx = new HashMap<String, Object>();
      ctx.put("n", new Namespace(Map.of("x", Value.of(42L))));
      assertThat(env.fromString("{{ n }}").render(ctx)).isNotEmpty();
    }
  }
}
