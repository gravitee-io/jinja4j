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

/** Group lv.items() into batches of a fixed size, with optional fill value. */
public final class BatchFilter implements NamedFilter {

  public static final BatchFilter INSTANCE = new BatchFilter();

  private BatchFilter() {}

  @Override
  public String name() {
    return "batch";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (!(v instanceof Value.ListVal lv) || args.isEmpty()) return v;
    int size = (int) args.getFirst().asLong();
    var fill = args.size() > 1 ? args.get(1) : null;
    var batches = new ArrayList<Value>();
    for (int i = 0; i < lv.items().size(); i += size) {
      var batch = new ArrayList<>(lv.items().subList(i, Math.min(i + size, lv.items().size())));
      if (fill != null) {
        while (batch.size() < size) batch.add(fill);
      }
      batches.add(Value.ofList(batch));
    }
    return Value.ofList(batches);
  }
}
