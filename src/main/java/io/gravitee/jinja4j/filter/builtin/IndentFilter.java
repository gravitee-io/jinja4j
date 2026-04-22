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

/** Indent each line of a string by a given width. */
public final class IndentFilter implements NamedFilter {

  public static final IndentFilter INSTANCE = new IndentFilter();

  private IndentFilter() {}

  @Override
  public String name() {
    return "indent";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    int width = !args.isEmpty() ? (int) args.getFirst().asLong() : 4;
    boolean indentFirst = args.size() > 1 && args.get(1).isTruthy();
    var s = v.asString();
    var indent = " ".repeat(width);
    var lines = s.split("\n", -1);
    var sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) sb.append("\n");
      if ((i > 0 || indentFirst) && !lines[i].isEmpty()) sb.append(indent);
      sb.append(lines[i]);
    }
    return Value.of(sb.toString());
  }
}
