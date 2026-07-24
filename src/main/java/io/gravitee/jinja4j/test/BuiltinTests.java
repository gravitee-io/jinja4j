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

import io.gravitee.jinja4j.test.builtin.*;

/**
 * Facade over the built-in test registry.
 *
 * <p>Internally this class pre-populates a {@link TestRegistry} with every
 * built-in test from {@code io.gravitee.jinja4j.test.builtin}. Use
 * {@link #registry()} to inspect or copy it.</p>
 */
public final class BuiltinTests {

  private BuiltinTests() {}

  private static final TestRegistry REGISTRY = buildDefaults();

  /** Shared singleton registry of built-in tests. */
  public static TestRegistry registry() {
    return REGISTRY;
  }

  /** Look up a built-in test by name or alias, or {@code null} if absent. */
  public static TestFunction get(String name) {
    return REGISTRY.get(name).orElse(null);
  }

  /** Whether a built-in test with the given name or alias exists. */
  public static boolean has(String name) {
    return REGISTRY.has(name);
  }

  private static TestRegistry buildDefaults() {
    return TestRegistry.empty()
      .register(DefinedTest.INSTANCE)
      .register(UndefinedTest.INSTANCE)
      .register(NoneTest.INSTANCE)
      .register(BooleanTest.INSTANCE)
      .register(IntegerTest.INSTANCE)
      .register(FloatTypeTest.INSTANCE)
      .register(NumberTest.INSTANCE)
      .register(StringTest.INSTANCE)
      .register(SequenceTest.INSTANCE)
      .register(MappingTest.INSTANCE)
      .register(IterableTest.INSTANCE)
      .register(CallableTest.INSTANCE)
      .register(OddTest.INSTANCE)
      .register(EvenTest.INSTANCE)
      .register(DivisiblebyTest.INSTANCE)
      .register(SameasTest.INSTANCE)
      .register(EqTest.INSTANCE)
      .register(NeTest.INSTANCE)
      .register(LtTest.INSTANCE)
      .register(LeTest.INSTANCE)
      .register(GtTest.INSTANCE)
      .register(GeTest.INSTANCE)
      .register(TrueTest.INSTANCE)
      .register(FalseTest.INSTANCE)
      .register(LowerTest.INSTANCE)
      .register(UpperTest.INSTANCE)
      .register(EscapedTest.INSTANCE);
  }
}
