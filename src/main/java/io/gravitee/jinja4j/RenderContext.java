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
package io.gravitee.jinja4j;

import io.gravitee.jinja4j.ast.Node;
import io.gravitee.jinja4j.value.Value;
import java.util.*;

/**
 * Manages variable scoping during template rendering.
 * Supports nested scopes for for-loops, with-blocks, macros, etc.
 */
public final class RenderContext {

  private final Deque<Map<String, Value>> scopes = new ArrayDeque<>();
  private final Map<String, List<Node>> blocks = new LinkedHashMap<>();

  public RenderContext() {
    scopes.push(new LinkedHashMap<>());
  }

  /**
   * Set a variable in the current (top) scope.
   */
  public void set(String name, Value value) {
    scopes.peek().put(name, value);
  }

  /**
   * Resolve a variable by walking up the scope chain.
   */
  public Value resolve(String name) {
    for (var scope : scopes) {
      var val = scope.get(name);
      if (val != null) return val;
    }
    return Value.UNDEFINED;
  }

  /**
   * Push a new scope (for for-loops, macros, with-blocks).
   * Returns this context for chaining.
   */
  public RenderContext pushScope() {
    scopes.push(new LinkedHashMap<>());
    return this;
  }

  /**
   * Pop the top scope.
   */
  public void popScope() {
    if (scopes.size() > 1) {
      scopes.pop();
    }
  }

  /**
   * Register a block override (for template inheritance).
   */
  public void setBlock(String name, List<Node> body) {
    blocks.put(name, body);
  }

  /**
   * Get a block override, or null if none.
   */
  public List<Node> getBlock(String name) {
    return blocks.get(name);
  }

  /**
   * Import all variables from a map into the current scope.
   */
  public void importAll(Map<String, Value> vars) {
    scopes.peek().putAll(vars);
  }
}
