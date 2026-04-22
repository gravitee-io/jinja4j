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
package io.gravitee.jinja4j.value;

import java.lang.reflect.*;
import java.util.*;

/**
 * Converts Java objects (Maps, Collections, primitives, POJOs) into the
 * template engine's {@link Value} type system.
 */
public final class ValueConverter {

  private ValueConverter() {}

  public static Value convert(Object obj) {
    if (obj == null) {
      return Value.NULL;
    }

    if (obj.getClass().isRecord()) {
      return convertRecord(obj);
    }

    return switch (obj) {
      case Value v -> v;
      case SafeString(String str) -> new Value.SafeStringVal(str);
      case String s -> Value.of(s);
      case Boolean b -> Value.of(b);
      case Integer i -> Value.of((long) i);
      case Long l -> Value.of(l);
      case Short s -> Value.of((long) s);
      case Byte b -> Value.of((long) b);
      case Float f -> Value.of((double) f);
      case Double d -> Value.of(d);
      case Namespace ns -> new Value.NamespaceVal(ns);
      case Map<?, ?> m -> {
        var map = new LinkedHashMap<String, Value>();
        for (var entry : m.entrySet()) {
          map.put(String.valueOf(entry.getKey()), convert(entry.getValue()));
        }
        yield Value.ofMap(map);
      }
      case Collection<?> c -> {
        var list = new ArrayList<Value>(c.size());
        for (var item : c) {
          list.add(convert(item));
        }
        yield Value.ofList(list);
      }
      case Object[] arr -> {
        var list = new ArrayList<Value>(arr.length);
        for (var item : arr) {
          list.add(convert(item));
        }
        yield Value.ofList(list);
      }
      default -> convertPojo(obj);
    };
  }

  private static Value convertRecord(Object record) {
    var map = new LinkedHashMap<String, Value>();
    for (var component : record.getClass().getRecordComponents()) {
      try {
        var accessor = component.getAccessor();
        var val = accessor.invoke(record);
        map.put(component.getName(), convert(val));
      } catch (ReflectiveOperationException e) {
        // skip inaccessible components
      }
    }
    return Value.ofMap(map);
  }

  private static Value convertPojo(Object pojo) {
    var map = new LinkedHashMap<String, Value>();
    for (var method : pojo.getClass().getMethods()) {
      if (method.getParameterCount() != 0) continue;
      if (method.getDeclaringClass() == Object.class) continue;
      var name = method.getName();
      String key = null;
      if (name.startsWith("get") && name.length() > 3) {
        key = Character.toLowerCase(name.charAt(3)) + name.substring(4);
      } else if (name.startsWith("is") && name.length() > 2 && method.getReturnType() == boolean.class) {
        key = Character.toLowerCase(name.charAt(2)) + name.substring(3);
      }
      if (key != null) {
        try {
          map.put(key, convert(method.invoke(pojo)));
        } catch (ReflectiveOperationException e) {
          // skip
        }
      }
    }
    return Value.ofMap(map);
  }

  /**
   * Convert a Value back to a plain Java object.
   */
  public static Object toJava(Value val) {
    return switch (val) {
      case Value.Undefined(), Value.NullVal() -> null;
      case Value.BoolVal(var b) -> b;
      case Value.IntVal(var i) -> i;
      case Value.FloatVal(var f) -> f;
      case Value.StringVal(var s) -> s;
      case Value.SafeStringVal(var s) -> s;
      case Value.ListVal(var items) -> {
        var list = new ArrayList<>(items.size());
        for (var item : items) list.add(toJava(item));
        yield list;
      }
      case Value.MapVal(var entries) -> {
        var map = new LinkedHashMap<String, Object>();
        for (var e : entries.entrySet()) map.put(e.getKey(), toJava(e.getValue()));
        yield map;
      }
      case Value.CallableVal(_, _) -> val;
      case Value.NamespaceVal(var ns) -> ns;
    };
  }
}
