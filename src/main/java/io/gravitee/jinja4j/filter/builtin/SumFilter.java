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

/** Sum of numeric list items, with optional starting value and attribute. */
public final class SumFilter implements NamedFilter {

  public static final SumFilter INSTANCE = new SumFilter();

  private SumFilter() {}

  @Override
  public String name() {
    return "sum";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (!(v instanceof Value.ListVal lv)) return Value.of(0);
    var attrName = kwargs.containsKey("attribute") ? kwargs.get("attribute").asString() : null;
    double sum = 0;
    boolean allInts = true;
    for (var item : lv.items()) {
      Value val = item;
      if (attrName != null && item instanceof Value.MapVal mv) {
        val = mv.entries().getOrDefault(attrName, Value.of(0));
      }
      if (!(val instanceof Value.IntVal)) allInts = false;
      sum += val.asDouble();
    }
    var start = !args.isEmpty() ? args.getFirst() : null;
    if (start != null) sum += start.asDouble();
    if (allInts && start == null) return Value.of((long) sum);
    return Value.of(sum);
  }
}
