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
package io.gravitee.jinja4j.interpreter.eval;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;

/**
 * Value-level equality, ordering, and containment.
 *
 * <p>{@link #equals} treats {@link Value.StringVal} and {@link Value.SafeStringVal}
 * as interchangeable when their characters match, and compares lists/maps by
 * deep element equality. {@link #compare} is defined only between two numbers
 * or two strings; anything else raises. {@link #contains} handles lists, maps
 * (by key), strings, and safe strings (by substring).</p>
 */
public final class ValueRelations {

  private ValueRelations() {}

  public static boolean equals(Value left, Value right) {
    // Nil-like values
    if (left instanceof Value.Undefined && right instanceof Value.Undefined) return true;
    if (left instanceof Value.NullVal && right instanceof Value.NullVal) return true;
    if (left instanceof Value.NullVal && right instanceof Value.Undefined) return false;
    if (left instanceof Value.Undefined && right instanceof Value.NullVal) return false;

    // Booleans
    if (left instanceof Value.BoolVal(boolean a) && right instanceof Value.BoolVal(boolean b)) {
      return a == b;
    }

    // Strings (including cross-equality with SafeStringVal)
    if (stringContent(left) != null && stringContent(right) != null) {
      return stringContent(left).equals(stringContent(right));
    }

    // Numbers — int/int uses exact equality, mixed uses double
    if (left.isNumber() && right.isNumber()) {
      if (left instanceof Value.IntVal(long a) && right instanceof Value.IntVal(long b)) {
        return a == b;
      }
      return left.asDouble() == right.asDouble();
    }

    // Collections — deep element-wise
    if (left instanceof Value.ListVal(List<Value> a) && right instanceof Value.ListVal(List<Value> b)) {
      return listEquals(a, b);
    }
    if (left instanceof Value.MapVal(Map<String, Value> a) && right instanceof Value.MapVal(Map<String, Value> b)) {
      return mapEquals(a, b);
    }

    return false;
  }

  /** Extracts the underlying {@code String} of a {@code StringVal} or {@code SafeStringVal}, or {@code null}. */
  private static String stringContent(Value v) {
    return switch (v) {
      case Value.StringVal(String s) -> s;
      case Value.SafeStringVal(String s) -> s;
      default -> null;
    };
  }

  private static boolean listEquals(List<Value> a, List<Value> b) {
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (!equals(a.get(i), b.get(i))) return false;
    }
    return true;
  }

  private static boolean mapEquals(Map<String, Value> a, Map<String, Value> b) {
    if (a.size() != b.size()) return false;
    for (var e : a.entrySet()) {
      var other = b.get(e.getKey());
      if (other == null || !equals(e.getValue(), other)) return false;
    }
    return true;
  }

  public static int compare(Value left, Value right, SourceLocation loc) {
    if (left.isNumber() && right.isNumber()) {
      return Double.compare(left.asDouble(), right.asDouble());
    }
    if (left instanceof Value.StringVal(String a) && right instanceof Value.StringVal(String b)) {
      return a.compareTo(b);
    }
    throw new TemplateException("Cannot compare %s and %s".formatted(left, right), loc);
  }

  public static boolean contains(Value container, Value item, SourceLocation loc) {
    return switch (container) {
      case Value.ListVal(List<Value> items) -> listContains(items, item);
      case Value.MapVal(Map<String, Value> entries) -> entries.containsKey(item.asString());
      case Value.StringVal(String s) -> s.contains(item.asString());
      case Value.SafeStringVal(String s) -> s.contains(item.asString());
      default -> throw new TemplateException("Cannot test membership in %s".formatted(container), loc);
    };
  }

  private static boolean listContains(List<Value> items, Value item) {
    for (var v : items) {
      if (equals(v, item)) return true;
    }
    return false;
  }
}
