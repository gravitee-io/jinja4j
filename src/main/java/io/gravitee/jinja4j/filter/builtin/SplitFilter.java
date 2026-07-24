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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Split a string into a list of substrings, Python {@code str.split} style.
 *
 * <p>Without arguments (or with {@code none}) the string is split on runs of whitespace and empty
 * parts are dropped. With a separator argument the string is split on every literal occurrence of
 * the separator, keeping empty parts. An optional {@code maxsplit} limits the number of splits.
 */
public final class SplitFilter implements NamedFilter {

  public static final SplitFilter INSTANCE = new SplitFilter();

  private SplitFilter() {}

  @Override
  public String name() {
    return "split";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    var s = v.asString();
    Value sepValue = !args.isEmpty() ? args.getFirst() : Value.NULL;
    int maxsplit = args.size() > 1
      ? (int) args.get(1).asLong()
      : (kwargs.containsKey("maxsplit") ? (int) kwargs.get("maxsplit").asLong() : -1);

    List<Value> parts = new ArrayList<>();
    if (sepValue instanceof Value.Undefined || sepValue instanceof Value.NullVal) {
      int i = 0;
      int splits = 0;
      while (i < s.length()) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i >= s.length()) break;
        if (maxsplit >= 0 && splits >= maxsplit) {
          parts.add(Value.of(s.substring(i)));
          break;
        }
        int start = i;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
        parts.add(Value.of(s.substring(start, i)));
        splits++;
      }
      // With maxsplit, the remainder above already includes trailing whitespace like Python's
      // str.split(None, maxsplit) after skipping leading whitespace.
      return Value.ofList(parts);
    }

    var sep = sepValue.asString();
    if (sep.isEmpty()) {
      return Value.ofList(List.of(Value.of(s)));
    }
    int idx = 0;
    int splits = 0;
    while (true) {
      if (maxsplit >= 0 && splits >= maxsplit) break;
      int found = s.indexOf(sep, idx);
      if (found < 0) break;
      parts.add(Value.of(s.substring(idx, found)));
      idx = found + sep.length();
      splits++;
    }
    parts.add(Value.of(s.substring(idx)));
    return Value.ofList(parts);
  }
}
