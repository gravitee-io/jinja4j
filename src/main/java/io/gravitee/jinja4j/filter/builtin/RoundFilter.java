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
import io.gravitee.jinja4j.filter.NamedFilter;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;

/** Round a number with optional precision and method (common, ceil, floor). */
public final class RoundFilter implements NamedFilter {

  public static final RoundFilter INSTANCE = new RoundFilter();

  private RoundFilter() {}

  @Override
  public String name() {
    return "round";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    int precision = !args.isEmpty() ? (int) args.getFirst().asLong() : 0;
    var method = kwargs.containsKey("method") ? kwargs.get("method").asString() : "common";
    double d = v.asDouble();
    double factor = Math.pow(10, precision);
    return Value.of(
      switch (method) {
        case "ceil" -> Math.ceil(d * factor) / factor;
        case "floor" -> Math.floor(d * factor) / factor;
        default -> Math.round(d * factor) / factor;
      }
    );
  }
}
