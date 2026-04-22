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
package io.gravitee.jinja4j.interpreter.methods;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.value.TemplateFunction;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Python {@code dict}-style methods exposed as attributes on map values
 * ({@code get}, {@code keys}, {@code values}, {@code items}, {@code update}).
 *
 * <p>Unknown names fall through to a regular key lookup on the map.</p>
 */
public final class MapMethods {

  private MapMethods() {}

  public static Value lookup(Map<String, Value> entries, String attribute, SourceLocation loc) {
    return switch (attribute) {
      case "get" -> callable("dict.get", (args, kw) -> {
        if (args.isEmpty()) return Value.UNDEFINED;
        var key = args.getFirst().asString();
        var defaultVal = args.size() > 1 ? args.get(1) : Value.NULL;
        return entries.getOrDefault(key, defaultVal);
      });
      case "keys" -> callable("dict.keys", (args, kw) -> Value.ofList(entries.keySet().stream().map(Value::of).toList()));
      case "values" -> callable("dict.values", (args, kw) -> Value.ofList(new ArrayList<>(entries.values())));
      case "items" -> callable("dict.items", (args, kw) -> asItems(entries));
      case "update" -> callable("dict.update", (args, kw) -> update(entries, args, kw));
      default -> entries.getOrDefault(attribute, Value.UNDEFINED);
    };
  }

  private static Value asItems(Map<String, Value> entries) {
    var items = new ArrayList<Value>(entries.size());
    for (var e : entries.entrySet()) {
      items.add(Value.ofList(List.of(Value.of(e.getKey()), e.getValue())));
    }
    return Value.ofList(items);
  }

  private static Value update(Map<String, Value> entries, List<Value> args, Map<String, Value> kw) {
    if (!args.isEmpty() && args.getFirst() instanceof Value.MapVal(Map<String, Value> other)) {
      entries.putAll(other);
    }
    entries.putAll(kw);
    return Value.NULL;
  }

  private static Value callable(String name, TemplateFunction fn) {
    return Value.ofCallable(name, fn);
  }
}
