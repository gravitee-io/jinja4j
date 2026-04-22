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
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.value.TemplateFunction;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Python-style string methods exposed as callable attributes on string values.
 *
 * <p>Each method returns a {@link Value.CallableVal} that closes over the
 * underlying {@code String}. Unknown method names resolve to
 * {@link Value#UNDEFINED}.</p>
 *
 * <p>Organised by theme: predicates, case, trimming, search, transform.</p>
 */
public final class StringMethods {

  private StringMethods() {}

  public static Value lookup(String value, String method, SourceLocation loc) {
    return switch (method) {
      // ---- Predicates ----
      case "startswith" -> callable("str.startswith", (args, kw) ->
        args.isEmpty() ? Value.FALSE : Value.of(value.startsWith(args.getFirst().asString()))
      );
      case "endswith" -> callable("str.endswith", (args, kw) ->
        args.isEmpty() ? Value.FALSE : Value.of(value.endsWith(args.getFirst().asString()))
      );
      // ---- Case ----
      case "upper" -> callable("str.upper", (args, kw) -> Value.of(value.toUpperCase()));
      case "lower" -> callable("str.lower", (args, kw) -> Value.of(value.toLowerCase()));
      case "title" -> callable("str.title", (args, kw) -> Value.of(toTitleCase(value)));
      // ---- Trimming ----
      case "strip" -> callable("str.strip", (args, kw) -> strip(value, args, true, true));
      case "lstrip" -> callable("str.lstrip", (args, kw) -> strip(value, args, true, false));
      case "rstrip" -> callable("str.rstrip", (args, kw) -> strip(value, args, false, true));
      // ---- Search ----
      case "find" -> callable("str.find", (args, kw) ->
        args.isEmpty() ? Value.of(-1) : Value.of(value.indexOf(args.getFirst().asString()))
      );
      case "count" -> callable("str.count", (args, kw) ->
        args.isEmpty() ? Value.of(0) : Value.of(countOccurrences(value, args.getFirst().asString()))
      );
      // ---- Transform ----
      case "split" -> callable("str.split", (args, kw) -> split(value, args));
      case "replace" -> callable("str.replace", (args, kw) -> {
        if (args.size() < 2) throw new TemplateException("str.replace() requires 2 arguments", loc);
        return Value.of(value.replace(args.get(0).asString(), args.get(1).asString()));
      });
      case "join" -> callable("str.join", (args, kw) -> join(value, args));
      case "format" -> callable("str.format", (args, kw) -> format(value, args, kw));
      default -> Value.UNDEFINED;
    };
  }

  // ---- Helpers ----

  private static Value callable(String name, TemplateFunction fn) {
    return Value.ofCallable(name, fn);
  }

  private static String toTitleCase(String s) {
    var sb = new StringBuilder(s.length());
    boolean nextUpper = true;
    for (var c : s.toCharArray()) {
      if (!Character.isLetterOrDigit(c)) {
        sb.append(c);
        nextUpper = true;
      } else if (nextUpper) {
        sb.append(Character.toUpperCase(c));
        nextUpper = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }

  private static Value strip(String s, List<Value> args, boolean left, boolean right) {
    if (!args.isEmpty()) {
      return Value.of(stripChars(s, args.getFirst().asString(), left, right));
    }
    if (left && right) return Value.of(s.strip());
    if (left) return Value.of(s.stripLeading());
    return Value.of(s.stripTrailing());
  }

  private static String stripChars(String s, String chars, boolean left, boolean right) {
    int start = 0;
    int end = s.length();
    if (left) {
      while (start < end && chars.indexOf(s.charAt(start)) >= 0) start++;
    }
    if (right) {
      while (end > start && chars.indexOf(s.charAt(end - 1)) >= 0) end--;
    }
    return s.substring(start, end);
  }

  private static int countOccurrences(String s, String sub) {
    if (sub.isEmpty()) return 0;
    int count = 0;
    int idx = 0;
    while ((idx = s.indexOf(sub, idx)) >= 0) {
      count++;
      idx += sub.length();
    }
    return count;
  }

  private static Value split(String s, List<Value> args) {
    var sep = args.isEmpty() ? null : args.getFirst().asString();
    var parts = sep == null ? s.strip().split("\\s+") : s.split(Pattern.quote(sep), -1);
    var list = new ArrayList<Value>(parts.length);
    for (var p : parts) list.add(Value.of(p));
    return Value.ofList(list);
  }

  private static Value join(String separator, List<Value> args) {
    if (args.isEmpty()) return Value.of("");
    if (args.getFirst() instanceof Value.ListVal(List<Value> items)) {
      return Value.of(items.stream().map(Value::asString).collect(Collectors.joining(separator)));
    }
    return Value.of("");
  }

  private static Value format(String template, List<Value> args, java.util.Map<String, Value> kw) {
    var result = template;
    for (int i = 0; i < args.size(); i++) {
      result = result.replace("{" + i + "}", args.get(i).asString());
    }
    for (var e : kw.entrySet()) {
      result = result.replace("{" + e.getKey() + "}", e.getValue().asString());
    }
    return Value.of(result);
  }
}
