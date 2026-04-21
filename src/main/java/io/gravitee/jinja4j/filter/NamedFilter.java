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

import java.util.List;

/**
 * A filter that carries its own name and optional aliases.
 * Used by {@link FilterRegistry} to register a builtin or custom filter
 * under its canonical name plus any aliases in one call.
 */
public interface NamedFilter extends FilterFunction {
  /** Canonical name of the filter (e.g. "length"). */
  String name();

  /** Optional aliases (e.g. "count" for length, "d" for default). */
  default List<String> aliases() {
    return List.of();
  }
}
