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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable registry of tests (built-in or custom).
 *
 * <p>Entries are keyed by name. A {@link NamedTest} is stored under its
 * canonical name and all its aliases. Later registrations with the same
 * name override earlier ones silently.</p>
 */
public final class TestRegistry {

  private final Map<String, TestFunction> entries = new LinkedHashMap<>();

  private TestRegistry() {}

  /** Create an empty registry. */
  public static TestRegistry empty() {
    return new TestRegistry();
  }

  /** Create a registry pre-populated with all built-in tests. */
  public static TestRegistry defaults() {
    return BuiltinTests.registry().copy();
  }

  /** Register a named test under its canonical name and all aliases. */
  public TestRegistry register(NamedTest test) {
    entries.put(test.name(), test);
    for (var alias : test.aliases()) {
      entries.put(alias, test);
    }
    return this;
  }

  /** Register a test function under an arbitrary name. */
  public TestRegistry register(String name, TestFunction fn) {
    entries.put(name, fn);
    return this;
  }

  /** Remove the entry with the given name. No-op if absent. */
  public TestRegistry unregister(String name) {
    entries.remove(name);
    return this;
  }

  /** Look up a test by name. */
  public Optional<TestFunction> get(String name) {
    return Optional.ofNullable(entries.get(name));
  }

  /** Whether a test with the given name is registered. */
  public boolean has(String name) {
    return entries.containsKey(name);
  }

  /**
   * Merge another registry into this one. Entries from {@code other}
   * override entries in this registry on name collision.
   */
  public TestRegistry merge(TestRegistry other) {
    entries.putAll(other.entries);
    return this;
  }

  /** Return an independent copy of this registry. */
  public TestRegistry copy() {
    var c = new TestRegistry();
    c.entries.putAll(entries);
    return c;
  }

  /** Number of registered entries (including alias entries). */
  public int size() {
    return entries.size();
  }
}
