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

/** Convert a value to a list. */
public final class ListFilter implements NamedFilter {

  public static final ListFilter INSTANCE = new ListFilter();

  private ListFilter() {}

  @Override
  public String name() {
    return "list";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (v instanceof Value.ListVal) return v;
    if (v instanceof Value.StringVal sv) {
      var chars = new ArrayList<Value>();
      for (var c : sv.value().toCharArray()) chars.add(Value.of(String.valueOf(c)));
      return Value.ofList(chars);
    }
    if (v instanceof Value.MapVal mv) {
      return Value.ofList(mv.entries().keySet().stream().map(Value::of).toList());
    }
    return Value.ofList(List.of(v));
  }
}
