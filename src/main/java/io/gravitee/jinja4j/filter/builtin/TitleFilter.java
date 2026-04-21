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

/** Title-case a string (capitalize first letter of each word). */
public final class TitleFilter implements NamedFilter {

  public static final TitleFilter INSTANCE = new TitleFilter();

  private TitleFilter() {}

  @Override
  public String name() {
    return "title";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    var s = v.asString();
    var sb = new StringBuilder();
    boolean nextUpper = true;
    for (var c : s.toCharArray()) {
      if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
        sb.append(c);
        nextUpper = true;
      } else if (nextUpper) {
        sb.append(Character.toUpperCase(c));
        nextUpper = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return Value.of(sb.toString());
  }
}
