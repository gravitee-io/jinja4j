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
package io.gravitee.jinja4j.interpreter.access;

import io.gravitee.jinja4j.RenderContext;
import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.interpreter.methods.ListMethods;
import io.gravitee.jinja4j.interpreter.methods.MapMethods;
import io.gravitee.jinja4j.interpreter.methods.StringMethods;
import io.gravitee.jinja4j.value.Namespace;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Value-space navigation: attribute lookup, item lookup, slicing, iteration,
 * and tuple unpacking.
 *
 * <p>Stateless collection of static helpers. Separated from the interpreter
 * so that expression evaluation and attribute/item semantics can evolve
 * independently.</p>
 */
public final class ValueAccessor {

  private ValueAccessor() {}

  // ---- Attribute access ----

  /**
   * Resolve an attribute ({@code obj.name}) on a value, including Python-style
   * methods exposed by {@link StringMethods}, {@link ListMethods},
   * {@link MapMethods}. Unknown attributes resolve to {@link Value#UNDEFINED}.
   */
  public static Value getAttribute(Value obj, String attribute, SourceLocation loc) {
    return switch (obj) {
      case Value.MapVal(Map<String, Value> entries) -> MapMethods.lookup(entries, attribute, loc);
      case Value.NamespaceVal(Namespace ns) -> ns.get(attribute);
      case Value.StringVal(String s) -> StringMethods.lookup(s, attribute, loc);
      case Value.SafeStringVal(String s) -> StringMethods.lookup(s, attribute, loc);
      case Value.ListVal(List<Value> items) -> ListMethods.lookup(items, attribute, loc);
      default -> Value.UNDEFINED;
    };
  }

  // ---- Item access ----

  /**
   * Resolve an indexed lookup ({@code obj[key]}). Integer keys on lists/strings
   * support Python-style negative indexing. Non-integer keys on lists/strings,
   * and missing map keys, resolve to {@link Value#UNDEFINED}.
   */
  public static Value getItem(Value obj, Value key, SourceLocation loc) {
    return switch (obj) {
      case Value.MapVal(Map<String, Value> entries) -> entries.getOrDefault(key.asString(), Value.UNDEFINED);
      case Value.NamespaceVal(Namespace ns) -> ns.get(key.asString());
      case Value.ListVal(List<Value> items) -> listItem(items, key);
      case Value.StringVal(String s) -> stringChar(s, key);
      default -> Value.UNDEFINED;
    };
  }

  private static Value listItem(List<Value> items, Value key) {
    if (!(key instanceof Value.IntVal(long idxRaw))) return Value.UNDEFINED;
    int idx = (int) idxRaw;
    if (idx < 0) idx = items.size() + idx;
    return (idx >= 0 && idx < items.size()) ? items.get(idx) : Value.UNDEFINED;
  }

  private static Value stringChar(String s, Value key) {
    if (!(key instanceof Value.IntVal(long idxRaw))) return Value.UNDEFINED;
    int idx = (int) idxRaw;
    if (idx < 0) idx = s.length() + idx;
    return (idx >= 0 && idx < s.length()) ? Value.of(String.valueOf(s.charAt(idx))) : Value.UNDEFINED;
  }

  // ---- Slicing ----

  /**
   * Apply a Python-style slice to a list or string. {@code null} bounds mean
   * "use the step-direction default".
   *
   * @throws TemplateException if {@code obj} is neither a list nor a string.
   */
  public static Value slice(Value obj, Long start, Long stop, Long step, SourceLocation loc) {
    int stepI = step != null ? step.intValue() : 1;
    return switch (obj) {
      case Value.ListVal(List<Value> items) -> sliceList(items, start, stop, stepI);
      case Value.StringVal(String s) -> sliceString(s, start, stop, stepI);
      default -> throw new TemplateException("Cannot slice %s".formatted(obj), loc);
    };
  }

  private static Value sliceList(List<Value> items, Long startL, Long stopL, int step) {
    var bounds = boundsFor(startL, stopL, step, items.size());
    var result = new ArrayList<Value>();
    if (step > 0) {
      for (int i = bounds.start(); i < bounds.stop(); i += step) result.add(items.get(i));
    } else if (step < 0) {
      for (int i = bounds.start(); i > bounds.stop(); i += step) result.add(items.get(i));
    }
    return Value.ofList(result);
  }

  private static Value sliceString(String s, Long startL, Long stopL, int step) {
    var bounds = boundsFor(startL, stopL, step, s.length());
    var sb = new StringBuilder();
    if (step > 0) {
      for (int i = bounds.start(); i < bounds.stop(); i += step) sb.append(s.charAt(i));
    } else if (step < 0) {
      for (int i = bounds.start(); i > bounds.stop(); i += step) sb.append(s.charAt(i));
    }
    return Value.of(sb.toString());
  }

  private record Bounds(int start, int stop) {}

  private static Bounds boundsFor(Long startL, Long stopL, int step, int len) {
    if (step > 0) {
      int start = startL != null ? normalizeIndex(startL.intValue(), len) : 0;
      int stop = stopL != null ? normalizeIndex(stopL.intValue(), len) : len;
      return new Bounds(start, stop);
    }
    int start = startL != null ? normalizeIndex(startL.intValue(), len) : len - 1;
    int stop = stopL != null ? normalizeNegStop(stopL.intValue(), len) : -1;
    return new Bounds(start, stop);
  }

  private static int normalizeIndex(int idx, int len) {
    if (idx < 0) idx = len + idx;
    return Math.max(0, Math.min(idx, len));
  }

  /** For negative-step stop: allows -1 to mean "before the first element". */
  private static int normalizeNegStop(int idx, int len) {
    if (idx < 0) idx = len + idx;
    return Math.max(-1, Math.min(idx, len));
  }

  // ---- Iteration ----

  /**
   * Convert a value to an iterable list. {@code null} and {@link Value.Undefined}
   * are treated as empty. Maps iterate over their keys, strings iterate over
   * their characters.
   *
   * @throws TemplateException for non-iterable types.
   */
  public static List<Value> toIterable(Value val, SourceLocation loc) {
    return switch (val) {
      case Value.ListVal(List<Value> items) -> items;
      case Value.MapVal(Map<String, Value> entries) -> entries.keySet().stream().map(Value::of).toList();
      case Value.StringVal(String s) -> s
        .chars()
        .mapToObj(c -> Value.of(String.valueOf((char) c)))
        .toList();
      case Value.Undefined ignored -> List.of();
      case Value.NullVal ignored -> List.of();
      default -> throw new TemplateException("Cannot iterate over %s".formatted(val), loc);
    };
  }

  // ---- Unpacking ----

  /**
   * Bind a single iterated item into multiple loop targets
   * ({@code for a, b in ...}). Missing positions are bound to
   * {@link Value#UNDEFINED}.
   *
   * @throws TemplateException if {@code item} is neither a list nor a map.
   */
  public static void unpackInto(Value item, List<String> targets, RenderContext scope, SourceLocation loc) {
    switch (item) {
      case Value.ListVal(List<Value> items) -> bindPositional(targets, items, scope);
      case Value.MapVal(Map<String, Value> entries) -> bindPositional(
        targets,
        entries.keySet().stream().<Value>map(Value::of).toList(),
        scope
      );
      default -> throw new TemplateException("Cannot unpack %s into %d variables".formatted(item, targets.size()), loc);
    }
  }

  private static void bindPositional(List<String> targets, List<Value> values, RenderContext scope) {
    for (int i = 0; i < targets.size(); i++) {
      scope.set(targets.get(i), i < values.size() ? values.get(i) : Value.UNDEFINED);
    }
  }
}
