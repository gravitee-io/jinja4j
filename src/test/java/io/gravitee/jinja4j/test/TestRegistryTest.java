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
package io.gravitee.jinja4j.test;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.test.builtin.*;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestRegistryTest {

  @Test
  void emptyRegistry() {
    var r = TestRegistry.empty();
    assertThat(r.size()).isZero();
    assertThat(r.has("defined")).isFalse();
    assertThat(r.get("defined")).isEmpty();
  }

  @Test
  void defaultsContainsAllBuiltins() {
    var r = TestRegistry.defaults();
    assertThat(r.has("defined")).isTrue();
    assertThat(r.has("undefined")).isTrue();
    assertThat(r.has("none")).isTrue();
    assertThat(r.has("eq")).isTrue();
    assertThat(r.has("equalto")).isTrue(); // alias of eq
    assertThat(r.has("==")).isTrue(); // alias of eq
    assertThat(r.has("lt")).isTrue();
    assertThat(r.has("lessthan")).isTrue(); // alias of lt
    assertThat(r.has("gt")).isTrue();
    assertThat(r.has("greaterthan")).isTrue(); // alias of gt
  }

  @Test
  void defaultsIsIndependentCopy() {
    var a = TestRegistry.defaults();
    var b = TestRegistry.defaults();
    a.unregister("defined");
    assertThat(a.has("defined")).isFalse();
    assertThat(b.has("defined")).isTrue();
  }

  @Test
  void registerNamedTest() {
    var r = TestRegistry.empty().register(DefinedTest.INSTANCE);
    assertThat(r.has("defined")).isTrue();
    assertThat(r.size()).isEqualTo(1);
  }

  @Test
  void registerNamedTestWithAliases() {
    var r = TestRegistry.empty().register(EqTest.INSTANCE);
    assertThat(r.has("eq")).isTrue();
    assertThat(r.has("equalto")).isTrue();
    assertThat(r.has("==")).isTrue();
    assertThat(r.get("eq").orElseThrow()).isSameAs(r.get("equalto").orElseThrow());
    assertThat(r.get("eq").orElseThrow()).isSameAs(r.get("==").orElseThrow());
  }

  @Test
  void registerFunctionByName() {
    TestFunction isEven = (v, args, loc) -> v.isInteger() && v.asLong() % 2 == 0;
    var r = TestRegistry.empty().register("my_even", isEven);
    assertThat(r.get("my_even").orElseThrow().test(Value.of(4L), List.of(), SourceLocation.UNKNOWN)).isTrue();
    assertThat(r.get("my_even").orElseThrow().test(Value.of(3L), List.of(), SourceLocation.UNKNOWN)).isFalse();
  }

  @Test
  void unregister() {
    var r = TestRegistry.defaults();
    assertThat(r.has("defined")).isTrue();
    r.unregister("defined");
    assertThat(r.has("defined")).isFalse();
  }

  @Test
  void unregisterAbsentIsNoOp() {
    var r = TestRegistry.empty();
    r.unregister("nothing");
    assertThat(r.size()).isZero();
  }

  @Test
  void reRegisterOverrides() {
    TestFunction alwaysTrue = (v, args, loc) -> true;
    TestFunction alwaysFalse = (v, args, loc) -> false;
    var r = TestRegistry.empty().register("x", alwaysTrue).register("x", alwaysFalse);
    assertThat(r.get("x").orElseThrow().test(Value.NULL, List.of(), SourceLocation.UNKNOWN)).isFalse();
  }

  @Test
  void mergeOtherOverrides() {
    TestFunction alwaysTrue = (v, args, loc) -> true;
    TestFunction alwaysFalse = (v, args, loc) -> false;
    var base = TestRegistry.empty().register("x", alwaysTrue).register("y", alwaysTrue);
    var overlay = TestRegistry.empty().register("x", alwaysFalse);
    base.merge(overlay);
    assertThat(base.get("x").orElseThrow().test(Value.NULL, List.of(), SourceLocation.UNKNOWN)).isFalse();
    assertThat(base.get("y").orElseThrow().test(Value.NULL, List.of(), SourceLocation.UNKNOWN)).isTrue();
  }

  @Test
  void copyIsIndependent() {
    var a = TestRegistry.empty().register(DefinedTest.INSTANCE);
    var b = a.copy();
    b.unregister("defined");
    assertThat(a.has("defined")).isTrue();
    assertThat(b.has("defined")).isFalse();
  }

  @Test
  void fluentChaining() {
    var r = TestRegistry.empty().register(DefinedTest.INSTANCE).register(UndefinedTest.INSTANCE).register(NoneTest.INSTANCE);
    assertThat(r.size()).isEqualTo(3);
  }

  @Test
  void builtinTestsFacadeAccessor() {
    assertThat(BuiltinTests.registry().has("defined")).isTrue();
    assertThat(BuiltinTests.get("defined")).isNotNull();
    assertThat(BuiltinTests.get("nonexistent")).isNull();
    assertThat(BuiltinTests.has("defined")).isTrue();
  }

  // ---- Integration with Environment ----

  @Test
  void customTestViaEnvironmentOverridesBuiltin() {
    var env = new Environment();
    // Override 'odd' to always return false
    env.addTest(
      new NamedTest() {
        @Override
        public String name() {
          return "odd";
        }

        @Override
        public boolean test(Value value, List<Value> args, SourceLocation loc) {
          return false;
        }
      }
    );
    assertThat(env.fromString("{{ 3 is odd }}").render()).isEqualTo("False");
    assertThat(env.fromString("{{ 4 is odd }}").render()).isEqualTo("False");
  }

  @Test
  void customTestWithAliasesOverridesBuiltin() {
    var env = new Environment();
    env.addTest(
      new NamedTest() {
        @Override
        public String name() {
          return "eq";
        }

        @Override
        public List<String> aliases() {
          return List.of("equalto");
        }

        @Override
        public boolean test(Value value, List<Value> args, SourceLocation loc) {
          return false; // always unequal
        }
      }
    );
    assertThat(env.fromString("{{ 1 is eq(1) }}").render()).isEqualTo("False");
    assertThat(env.fromString("{{ 1 is equalto(1) }}").render()).isEqualTo("False");
  }

  @Test
  void deprecatedAddTestStillWorks() {
    var env = new Environment();
    env.addTest("positive", (v, args, loc) -> v.isNumber() && v.asDouble() > 0);
    assertThat(env.fromString("{{ 5 is positive }}").render()).isEqualTo("True");
    assertThat(env.fromString("{{ (-1) is positive }}").render()).isEqualTo("False");
  }

  @Test
  void environmentTestsRegistryAccessor() {
    var env = new Environment();
    var registry = env.tests();
    assertThat(registry.has("mytest")).isFalse();
    registry.register("mytest", (v, args, loc) -> true);
    assertThat(env.fromString("{{ 'x' is mytest }}").render()).isEqualTo("True");
  }

  @Test
  void unknownTestStillThrows() {
    var env = new Environment();
    assertThatThrownBy(() -> env.fromString("{{ 'x' is nonexistent_test }}").render(java.util.Map.of("x", 1)))
      .isInstanceOf(io.gravitee.jinja4j.TemplateException.class)
      .hasMessageContaining("Unknown test");
  }
}
