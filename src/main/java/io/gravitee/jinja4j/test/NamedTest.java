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

import java.util.List;

/**
 * A test that carries its own name and optional aliases.
 * Used by {@link TestRegistry} to register a builtin or custom test
 * under its canonical name plus any aliases in one call.
 */
public interface NamedTest extends TestFunction {
  /** Canonical name of the test (e.g. "eq"). */
  String name();

  /** Optional aliases (e.g. "equalto", "==" for eq). */
  default List<String> aliases() {
    return List.of();
  }
}
