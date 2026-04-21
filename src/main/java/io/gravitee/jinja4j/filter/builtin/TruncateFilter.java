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

/** Truncate a string to a maximum length with a terminator. */
public final class TruncateFilter implements NamedFilter {

  public static final TruncateFilter INSTANCE = new TruncateFilter();

  private TruncateFilter() {}

  @Override
  public String name() {
    return "truncate";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    int length = !args.isEmpty() ? (int) args.getFirst().asLong() : 255;
    var end = args.size() > 1 ? args.get(1).asString() : "...";
    var s = v.asString();
    if (s.length() <= length) return Value.of(s);
    return Value.of(s.substring(0, length - end.length()) + end);
  }
}
