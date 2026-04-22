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

/** Sort a list. Supports {@code reverse=} and {@code attribute=} kwargs. */
public final class SortFilter implements NamedFilter {

  public static final SortFilter INSTANCE = new SortFilter();

  private SortFilter() {}

  @Override
  public String name() {
    return "sort";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (v instanceof Value.ListVal lv) {
      var sorted = new ArrayList<>(lv.items());
      boolean reverse = kwargs.containsKey("reverse") && kwargs.get("reverse").isTruthy();
      var attrName = kwargs.containsKey("attribute") ? kwargs.get("attribute").asString() : null;
      sorted.sort((a, b) -> {
        Value va = a,
          vb = b;
        if (attrName != null) {
          if (a instanceof Value.MapVal ma) va = ma.entries().getOrDefault(attrName, Value.UNDEFINED);
          if (b instanceof Value.MapVal mb) vb = mb.entries().getOrDefault(attrName, Value.UNDEFINED);
        }
        int cmp;
        if (va.isNumber() && vb.isNumber()) {
          cmp = Double.compare(va.asDouble(), vb.asDouble());
        } else {
          cmp = va.asString().compareTo(vb.asString());
        }
        return reverse ? -cmp : cmp;
      });
      return Value.ofList(sorted);
    }
    return v;
  }
}
