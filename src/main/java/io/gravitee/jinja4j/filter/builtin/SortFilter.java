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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sort a list. Supports {@code reverse=} and {@code attribute=} kwargs.
 *
 * <p>{@code attribute} may name multiple keys separated by commas
 * ({@code attribute="last,first"}) and each key may be a dotted path
 * ({@code attribute="user.name"}). The sort is stable; {@code reverse=true}
 * preserves the stable ordering of equal elements (it sorts ascending and
 * then reverses).</p>
 */
public final class SortFilter implements NamedFilter {

  public static final SortFilter INSTANCE = new SortFilter();

  private SortFilter() {}

  @Override
  public String name() {
    return "sort";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (!(v instanceof Value.ListVal lv)) return v;
    var sorted = new ArrayList<>(lv.items());
    boolean reverse = kwargs.containsKey("reverse") && kwargs.get("reverse").isTruthy();
    boolean caseSensitive = kwargs.containsKey("case_sensitive") && kwargs.get("case_sensitive").isTruthy();
    var keys = parseAttributes(kwargs.get("attribute"));

    sorted.sort((a, b) -> compareByKeys(a, b, keys, caseSensitive));
    if (reverse) Collections.reverse(sorted);
    return Value.ofList(sorted);
  }

  private static List<List<String>> parseAttributes(Value attribute) {
    if (attribute == null || attribute.isNull() || attribute.isUndefined()) return List.of();
    var keys = new ArrayList<List<String>>();
    for (var part : attribute.asString().split(",")) {
      var path = new ArrayList<String>();
      for (var seg : part.strip().split("\\.")) {
        if (!seg.isEmpty()) path.add(seg);
      }
      if (!path.isEmpty()) keys.add(path);
    }
    return keys;
  }

  private static int compareByKeys(Value a, Value b, List<List<String>> keys, boolean caseSensitive) {
    if (keys.isEmpty()) return compareValues(a, b, caseSensitive);
    for (var key : keys) {
      int cmp = compareValues(resolve(a, key), resolve(b, key), caseSensitive);
      if (cmp != 0) return cmp;
    }
    return 0;
  }

  private static Value resolve(Value v, List<String> path) {
    var current = v;
    for (var seg : path) {
      if (current instanceof Value.MapVal mv) {
        current = mv.entries().getOrDefault(seg, Value.UNDEFINED);
      } else {
        return Value.UNDEFINED;
      }
    }
    return current;
  }

  private static int compareValues(Value va, Value vb, boolean caseSensitive) {
    if (va.isNumber() && vb.isNumber()) {
      return Double.compare(va.asDouble(), vb.asDouble());
    }
    var sa = va.asString();
    var sb = vb.asString();
    return caseSensitive ? sa.compareTo(sb) : sa.compareToIgnoreCase(sb);
  }
}
