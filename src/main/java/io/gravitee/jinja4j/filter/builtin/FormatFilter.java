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
package io.gravitee.jinja4j.filter.builtin;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.filter.NamedFilter;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * printf-style string formatting: {@code "%s-%d"|format(name, count)}.
 *
 * <p>Mirrors Jinja2's {@code format} filter (Python {@code %} formatting).
 * Each positional argument is converted to the Java type expected by
 * {@link String#format}: integers become {@code Long}, floats {@code Double},
 * everything else its string form.</p>
 */
public final class FormatFilter implements NamedFilter {

  public static final FormatFilter INSTANCE = new FormatFilter();

  private FormatFilter() {}

  @Override
  public String name() {
    return "format";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    var objs = new Object[args.size()];
    for (int i = 0; i < args.size(); i++) {
      objs[i] = switch (args.get(i)) {
        case Value.IntVal(long n) -> n;
        case Value.FloatVal(double d) -> d;
        case Value.BoolVal(boolean b) -> b;
        default -> args.get(i).asString();
      };
    }
    try {
      return Value.of(String.format(Locale.ROOT, v.asString(), objs));
    } catch (java.util.IllegalFormatException e) {
      throw new TemplateException("Invalid format string '%s': %s".formatted(v.asString(), e.getMessage()), loc);
    }
  }
}
