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
import io.gravitee.jinja4j.filter.FilterSupport;
import io.gravitee.jinja4j.filter.NamedFilter;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;

/** Select lv.items() from a list that match a named test or are truthy. */
public final class SelectFilter implements NamedFilter {

  public static final SelectFilter INSTANCE = new SelectFilter();

  private SelectFilter() {}

  @Override
  public String name() {
    return "select";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (v instanceof Value.ListVal lv) {
      var result = lv
        .items()
        .stream()
        .filter(item -> FilterSupport.applyTestPredicate(item, args))
        .toList();
      return Value.ofList(result);
    }
    return v;
  }
}
