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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable registry of filters (built-in or custom).
 *
 * <p>Entries are keyed by name. A {@link NamedFilter} is stored under its
 * canonical name and all its aliases. Later registrations with the same
 * name override earlier ones silently.</p>
 */
public final class FilterRegistry {

  private final Map<String, FilterFunction> entries = new LinkedHashMap<>();

  private FilterRegistry() {}

  /** Create an empty registry. */
  public static FilterRegistry empty() {
    return new FilterRegistry();
  }

  /** Create a registry pre-populated with all built-in filters. */
  public static FilterRegistry defaults() {
    return BuiltinFilters.registry().copy();
  }

  /** Register a named filter under its canonical name and all aliases. */
  public FilterRegistry register(NamedFilter filter) {
    entries.put(filter.name(), filter);
    for (var alias : filter.aliases()) {
      entries.put(alias, filter);
    }
    return this;
  }

  /** Register a filter function under an arbitrary name. */
  public FilterRegistry register(String name, FilterFunction fn) {
    entries.put(name, fn);
    return this;
  }

  /** Remove the entry with the given name. No-op if absent. */
  public FilterRegistry unregister(String name) {
    entries.remove(name);
    return this;
  }

  /** Look up a filter by name. */
  public Optional<FilterFunction> get(String name) {
    return Optional.ofNullable(entries.get(name));
  }

  /** Whether a filter with the given name is registered. */
  public boolean has(String name) {
    return entries.containsKey(name);
  }

  /**
   * Merge another registry into this one. Entries from {@code other}
   * override entries in this registry on name collision.
   */
  public FilterRegistry merge(FilterRegistry other) {
    entries.putAll(other.entries);
    return this;
  }

  /** Return an independent copy of this registry. */
  public FilterRegistry copy() {
    var c = new FilterRegistry();
    c.entries.putAll(entries);
    return c;
  }

  /** Number of registered entries (including alias entries). */
  public int size() {
    return entries.size();
  }
}
