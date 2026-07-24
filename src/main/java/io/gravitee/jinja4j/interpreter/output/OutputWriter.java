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
package io.gravitee.jinja4j.interpreter.output;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Writes an evaluated {@link Value} into an output buffer, applying the
 * environment's undefined-strict and auto-escape policies.
 *
 * <p>Centralises the rules originally inlined in the interpreter's
 * {@code Output} node branch.</p>
 */
public final class OutputWriter {

  private final Environment env;

  /** Scoped {@code {% autoescape %}} overrides; the top entry wins over the environment default. */
  private final Deque<Boolean> autoEscapeOverrides = new ArrayDeque<>();

  public OutputWriter(Environment env) {
    this.env = env;
  }

  /** Push a scoped auto-escaping override (for {@code {% autoescape flag %}}). */
  public void pushAutoEscape(boolean enabled) {
    autoEscapeOverrides.push(enabled);
  }

  /** Pop the most recent auto-escaping override. */
  public void popAutoEscape() {
    if (!autoEscapeOverrides.isEmpty()) autoEscapeOverrides.pop();
  }

  private boolean autoEscaping() {
    return autoEscapeOverrides.isEmpty() ? env.isAutoEscaping() : autoEscapeOverrides.peek();
  }

  /**
   * Append a value to {@code sb}.
   *
   * <ul>
   *   <li>{@link Value.Undefined} renders as empty unless
   *       {@link Environment#isUndefinedBehaviorStrict()} is set, in which
   *       case a {@link TemplateException} is thrown.</li>
   *   <li>When {@link Environment#isAutoEscaping()} is on, all values
   *       except {@link Value.SafeStringVal} are HTML-escaped.</li>
   * </ul>
   */
  public void append(StringBuilder sb, Value value, SourceLocation loc) {
    if (value instanceof Value.Undefined) {
      if (env.isUndefinedBehaviorStrict()) {
        throw new TemplateException("Undefined value in output", loc);
      }
      return; // render nothing
    }
    var str = value.asString();
    if (autoEscaping() && !(value instanceof Value.SafeStringVal)) {
      str = HtmlEscaper.escape(str);
    }
    sb.append(str);
  }
}
