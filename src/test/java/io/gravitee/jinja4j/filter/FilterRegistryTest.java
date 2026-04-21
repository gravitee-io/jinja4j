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
package io.gravitee.jinja4j.filter;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.filter.builtin.*;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FilterRegistryTest {

  @Test
  void emptyRegistry() {
    var r = FilterRegistry.empty();
    assertThat(r.size()).isZero();
    assertThat(r.has("upper")).isFalse();
    assertThat(r.get("upper")).isEmpty();
  }

  @Test
  void defaultsContainsAllBuiltins() {
    var r = FilterRegistry.defaults();
    assertThat(r.has("upper")).isTrue();
    assertThat(r.has("lower")).isTrue();
    assertThat(r.has("length")).isTrue();
    assertThat(r.has("count")).isTrue(); // alias of length
    assertThat(r.has("default")).isTrue();
    assertThat(r.has("d")).isTrue(); // alias of default
    assertThat(r.has("escape")).isTrue();
    assertThat(r.has("e")).isTrue(); // alias of escape
    assertThat(r.has("forceescape")).isTrue(); // alias of escape
  }

  @Test
  void defaultsIsIndependentCopy() {
    var a = FilterRegistry.defaults();
    var b = FilterRegistry.defaults();
    a.unregister("upper");
    assertThat(a.has("upper")).isFalse();
    assertThat(b.has("upper")).isTrue();
  }

  @Test
  void registerNamedFilter() {
    var r = FilterRegistry.empty().register(UpperFilter.INSTANCE);
    assertThat(r.has("upper")).isTrue();
    assertThat(r.size()).isEqualTo(1);
  }

  @Test
  void registerNamedFilterWithAliases() {
    var r = FilterRegistry.empty().register(LengthFilter.INSTANCE);
    assertThat(r.has("length")).isTrue();
    assertThat(r.has("count")).isTrue();
    assertThat(r.get("length")).isPresent();
    assertThat(r.get("count").orElseThrow()).isSameAs(r.get("length").orElseThrow());
  }

  @Test
  void registerMultipleAliasFilter() {
    var r = FilterRegistry.empty().register(EscapeFilter.INSTANCE);
    assertThat(r.has("escape")).isTrue();
    assertThat(r.has("e")).isTrue();
    assertThat(r.has("forceescape")).isTrue();
  }

  @Test
  void registerFunctionByName() {
    FilterFunction reverse = (v, args, kwargs, loc) -> Value.of(new StringBuilder(v.asString()).reverse().toString());
    var r = FilterRegistry.empty().register("revcustom", reverse);
    var result = r.get("revcustom").orElseThrow().apply(Value.of("abc"), List.of(), Map.of(), SourceLocation.UNKNOWN);
    assertThat(result.asString()).isEqualTo("cba");
  }

  @Test
  void unregister() {
    var r = FilterRegistry.defaults();
    assertThat(r.has("upper")).isTrue();
    r.unregister("upper");
    assertThat(r.has("upper")).isFalse();
  }

  @Test
  void unregisterAbsentIsNoOp() {
    var r = FilterRegistry.empty();
    r.unregister("nothing");
    assertThat(r.size()).isZero();
  }

  @Test
  void reRegisterOverrides() {
    FilterFunction a = (v, args, kw, loc) -> Value.of("A");
    FilterFunction b = (v, args, kw, loc) -> Value.of("B");
    var r = FilterRegistry.empty().register("x", a).register("x", b);
    var result = r.get("x").orElseThrow().apply(Value.NULL, List.of(), Map.of(), SourceLocation.UNKNOWN);
    assertThat(result.asString()).isEqualTo("B");
  }

  @Test
  void mergeOtherOverrides() {
    FilterFunction a = (v, args, kw, loc) -> Value.of("A");
    FilterFunction b = (v, args, kw, loc) -> Value.of("B");
    var base = FilterRegistry.empty().register("x", a).register("y", a);
    var overlay = FilterRegistry.empty().register("x", b);
    base.merge(overlay);
    assertThat(
      base.get("x").orElseThrow().apply(Value.NULL, List.of(), Map.of(), SourceLocation.UNKNOWN).asString()
    ).isEqualTo("B");
    assertThat(
      base.get("y").orElseThrow().apply(Value.NULL, List.of(), Map.of(), SourceLocation.UNKNOWN).asString()
    ).isEqualTo("A");
  }

  @Test
  void copyIsIndependent() {
    var a = FilterRegistry.empty().register(UpperFilter.INSTANCE);
    var b = a.copy();
    b.unregister("upper");
    assertThat(a.has("upper")).isTrue();
    assertThat(b.has("upper")).isFalse();
  }

  @Test
  void fluentChaining() {
    var r = FilterRegistry.empty()
      .register(UpperFilter.INSTANCE)
      .register(LowerFilter.INSTANCE)
      .register(TrimFilter.INSTANCE);
    assertThat(r.size()).isEqualTo(3);
  }

  @Test
  void builtinFiltersFacadeAccessor() {
    assertThat(BuiltinFilters.registry().has("upper")).isTrue();
    assertThat(BuiltinFilters.get("upper")).isNotNull();
    assertThat(BuiltinFilters.get("nonexistent")).isNull();
    assertThat(BuiltinFilters.has("upper")).isTrue();
  }

  // ---- Integration with Environment ----

  @Test
  void customFilterViaEnvironmentOverridesBuiltin() {
    var env = new Environment();
    // Register custom "upper" that returns reversed string instead of uppercased
    env.addFilter(
      new NamedFilter() {
        @Override
        public String name() {
          return "upper";
        }

        @Override
        public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
          return Value.of(new StringBuilder(v.asString()).reverse().toString());
        }
      }
    );
    assertThat(env.fromString("{{ 'hello' | upper }}").render()).isEqualTo("olleh");
  }

  @Test
  void customFilterWithAliasesOverridesBuiltin() {
    var env = new Environment();
    env.addFilter(
      new NamedFilter() {
        @Override
        public String name() {
          return "length";
        }

        @Override
        public List<String> aliases() {
          return List.of("count");
        }

        @Override
        public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
          return Value.of(42L);
        }
      }
    );
    assertThat(env.fromString("{{ 'hello' | length }}").render()).isEqualTo("42");
    assertThat(env.fromString("{{ 'hello' | count }}").render()).isEqualTo("42");
  }

  @Test
  void deprecatedAddFilterStillWorks() {
    var env = new Environment();
    env.addFilter("shout", (v, args, kw, loc) -> Value.of(v.asString().toUpperCase() + "!"));
    assertThat(env.fromString("{{ 'hi' | shout }}").render()).isEqualTo("HI!");
  }

  @Test
  void environmentFiltersRegistryAccessor() {
    var env = new Environment();
    var registry = env.filters();
    assertThat(registry.has("myfilter")).isFalse();
    registry.register("myfilter", (v, args, kw, loc) -> Value.of("custom"));
    assertThat(env.fromString("{{ 'x' | myfilter }}").render()).isEqualTo("custom");
  }

  @Test
  void unknownFilterStillThrows() {
    var env = new Environment();
    assertThatThrownBy(() -> env.fromString("{{ 'x' | nonexistent_filter }}").render())
      .isInstanceOf(io.gravitee.jinja4j.TemplateException.class)
      .hasMessageContaining("Unknown filter");
  }
}
