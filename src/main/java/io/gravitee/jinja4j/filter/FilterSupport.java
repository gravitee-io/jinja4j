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
package io.gravitee.jinja4j.filter;

import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared helpers used by multiple built-in filters:
 * JSON serialization, named test predicates (used by select/reject/...)
 * and attribute extraction.
 */
public final class FilterSupport {

  private FilterSupport() {}

  // ---- JSON ----

  /** Compact JSON serialization of a Value. */
  public static String toJson(Value v) {
    return switch (v) {
      case Value.StringVal sv -> "\"" + escapeJsonString(sv.value()) + "\"";
      case Value.SafeStringVal ssv -> "\"" + escapeJsonString(ssv.value()) + "\"";
      case Value.IntVal iv -> Long.toString(iv.value());
      case Value.FloatVal fv -> Double.toString(fv.value());
      case Value.BoolVal bv -> bv.value() ? "true" : "false";
      case Value.NullVal ignored -> "null";
      case Value.Undefined ignored -> "null";
      case Value.ListVal lv -> "[" + lv.items().stream().map(FilterSupport::toJson).collect(Collectors.joining(", ")) + "]";
      case Value.MapVal mv -> {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var e : mv.entries().entrySet()) {
          if (!first) sb.append(", ");
          sb.append("\"").append(escapeJsonString(e.getKey())).append("\": ");
          sb.append(toJson(e.getValue()));
          first = false;
        }
        sb.append("}");
        yield sb.toString();
      }
      case Value.CallableVal ignored -> "null";
      case Value.NamespaceVal ignored -> "null";
    };
  }

  /** Pretty-printed JSON serialization with the given indent width. */
  public static String toJsonIndented(Value v, int indent, int depth) {
    var pad = " ".repeat(indent * depth);
    var innerPad = " ".repeat(indent * (depth + 1));
    return switch (v) {
      case Value.StringVal sv -> "\"" + escapeJsonString(sv.value()) + "\"";
      case Value.SafeStringVal ssv -> "\"" + escapeJsonString(ssv.value()) + "\"";
      case Value.IntVal iv -> Long.toString(iv.value());
      case Value.FloatVal fv -> Double.toString(fv.value());
      case Value.BoolVal bv -> bv.value() ? "true" : "false";
      case Value.NullVal ignored -> "null";
      case Value.Undefined ignored -> "null";
      case Value.ListVal lv -> {
        var items = lv.items();
        if (items.isEmpty()) yield "[]";
        var sb = new StringBuilder("[\n");
        for (int i = 0; i < items.size(); i++) {
          sb.append(innerPad).append(toJsonIndented(items.get(i), indent, depth + 1));
          if (i < items.size() - 1) sb.append(",");
          sb.append("\n");
        }
        sb.append(pad).append("]");
        yield sb.toString();
      }
      case Value.MapVal mv -> {
        var entries = mv.entries();
        if (entries.isEmpty()) yield "{}";
        var sb = new StringBuilder("{\n");
        var iter = entries.entrySet().iterator();
        while (iter.hasNext()) {
          var e = iter.next();
          sb.append(innerPad).append("\"").append(escapeJsonString(e.getKey())).append("\": ");
          sb.append(toJsonIndented(e.getValue(), indent, depth + 1));
          if (iter.hasNext()) sb.append(",");
          sb.append("\n");
        }
        sb.append(pad).append("}");
        yield sb.toString();
      }
      case Value.CallableVal ignored -> "null";
      case Value.NamespaceVal ignored -> "null";
    };
  }

  private static String escapeJsonString(String s) {
    var sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) sb.append("\\u%04x".formatted((int) c));
          else sb.append(c);
        }
      }
    }
    return sb.toString();
  }

  // ---- select/reject/selectattr/rejectattr helpers ----

  /**
   * Apply a named test predicate to a value. Used by select/reject/selectattr/rejectattr.
   * <ul>
   *   <li>No args: tests truthiness</li>
   *   <li>One arg (test name string): applies the named test</li>
   *   <li>Two args (test name and value): applies the named test with the value as argument</li>
   * </ul>
   */
  public static boolean applyTestPredicate(Value item, List<Value> args) {
    if (args.isEmpty()) return item.isTruthy();
    var testName = args.getFirst().asString();
    return switch (testName) {
      case "equalto", "eq", "sameas" -> args.size() > 1 && valueEquals(item, args.get(1));
      case "ne" -> args.size() <= 1 || !valueEquals(item, args.get(1));
      case "lt", "lessthan" -> args.size() > 1 && compareNum(item, args.get(1)) < 0;
      case "le" -> args.size() > 1 && compareNum(item, args.get(1)) <= 0;
      case "gt", "greaterthan" -> args.size() > 1 && compareNum(item, args.get(1)) > 0;
      case "ge" -> args.size() > 1 && compareNum(item, args.get(1)) >= 0;
      case "in" -> args.size() > 1 && containedIn(item, args.get(1));
      case "defined" -> !item.isUndefined();
      case "undefined" -> item.isUndefined();
      case "none" -> item instanceof Value.NullVal;
      case "true" -> item instanceof Value.BoolVal bv && bv.value();
      case "false" -> item instanceof Value.BoolVal bv && !bv.value();
      case "string" -> item.isString();
      case "number" -> item.isNumber();
      case "integer" -> item.isInteger();
      case "float" -> item.isFloat();
      case "mapping" -> item.isMapping();
      case "sequence", "iterable" -> item.isSequence() || item.isMapping() || item.isString();
      case "odd" -> item.isInteger() && item.asLong() % 2 != 0;
      case "even" -> item.isInteger() && item.asLong() % 2 == 0;
      case "divisibleby" -> args.size() > 1 &&
      item.isInteger() &&
      args.get(1).asLong() != 0 &&
      item.asLong() % args.get(1).asLong() == 0;
      default -> item.isTruthy();
    };
  }

  private static boolean valueEquals(Value a, Value b) {
    if (a.isNumber() && b.isNumber()) return a.asDouble() == b.asDouble();
    return a.asString().equals(b.asString());
  }

  private static int compareNum(Value a, Value b) {
    return Double.compare(a.asDouble(), b.asDouble());
  }

  private static boolean containedIn(Value item, Value container) {
    if (container instanceof Value.ListVal lv) return lv
      .items()
      .stream()
      .anyMatch(i -> valueEquals(i, item));
    if (container instanceof Value.StringVal sv) return sv.value().contains(item.asString());
    if (container instanceof Value.MapVal mv) return mv.entries().containsKey(item.asString());
    return false;
  }

  /** Extract a named attribute from a mapping. Returns UNDEFINED if absent or not a map. */
  public static Value getAttr(Value v, String attr) {
    if (v instanceof Value.MapVal mv) return mv.entries().getOrDefault(attr, Value.UNDEFINED);
    return Value.UNDEFINED;
  }
}
