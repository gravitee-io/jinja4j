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

/**
 * Map a list through an attribute extraction or a named simple transform.
 * Supported inline transforms: upper, lower, trim, string, int, float, capitalize.
 */
public final class MapFilter implements NamedFilter {

  public static final MapFilter INSTANCE = new MapFilter();

  private MapFilter() {}

  @Override
  public String name() {
    return "map";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (!(v instanceof Value.ListVal lv)) return v;
    if (kwargs.containsKey("attribute")) {
      var attrName = kwargs.get("attribute").asString();
      return Value.ofList(
        lv
          .items()
          .stream()
          .map(item -> {
            if (item instanceof Value.MapVal mv) {
              return mv.entries().getOrDefault(attrName, Value.UNDEFINED);
            }
            return Value.UNDEFINED;
          })
          .toList()
      );
    }
    if (!args.isEmpty()) {
      var filterName = args.getFirst().asString();
      return Value.ofList(
        lv
          .items()
          .stream()
          .map(item ->
            switch (filterName) {
              case "upper" -> Value.of(item.asString().toUpperCase());
              case "lower" -> Value.of(item.asString().toLowerCase());
              case "trim" -> Value.of(item.asString().strip());
              case "string" -> Value.of(item.asString());
              case "int" -> {
                try {
                  yield Value.of(item.asLong());
                } catch (Exception e) {
                  yield Value.of(0);
                }
              }
              case "float" -> {
                try {
                  yield Value.of(item.asDouble());
                } catch (Exception e) {
                  yield Value.of(0.0);
                }
              }
              case "capitalize" -> {
                var s = item.asString();
                yield s.isEmpty()
                  ? Value.of(s)
                  : Value.of(Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase());
              }
              default -> item;
            }
          )
          .toList()
      );
    }
    return v;
  }
}
