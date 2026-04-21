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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Group list items by an attribute, returning a list of {grouper, list} maps. */
public final class GroupbyFilter implements NamedFilter {

  public static final GroupbyFilter INSTANCE = new GroupbyFilter();

  private GroupbyFilter() {}

  @Override
  public String name() {
    return "groupby";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (!(v instanceof Value.ListVal lv) || args.isEmpty()) return v;
    var attrName = args.getFirst().asString();
    var groups = new LinkedHashMap<String, List<Value>>();
    for (var item : lv.items()) {
      String key = "";
      if (item instanceof Value.MapVal mv) {
        var keyVal = mv.entries().get(attrName);
        if (keyVal != null) key = keyVal.asString();
      }
      groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(item);
    }
    var result = new ArrayList<Value>();
    for (var e : groups.entrySet()) {
      var group = new LinkedHashMap<String, Value>();
      group.put("grouper", Value.of(e.getKey()));
      group.put("list", Value.ofList(e.getValue()));
      result.add(Value.ofMap(group));
    }
    return Value.ofList(result);
  }
}
