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
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.filter.NamedFilter;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;

/** Replace occurrences of a substring. */
public final class ReplaceFilter implements NamedFilter {

  public static final ReplaceFilter INSTANCE = new ReplaceFilter();

  private ReplaceFilter() {}

  @Override
  public String name() {
    return "replace";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (args.size() < 2) throw new TemplateException("replace() requires at least 2 arguments", loc);
    var old = args.get(0).asString();
    var newStr = args.get(1).asString();
    var count = args.size() > 2 ? (int) args.get(2).asLong() : -1;
    var s = v.asString();
    if (count < 0) {
      return Value.of(s.replace(old, newStr));
    }
    var sb = new StringBuilder();
    int idx = 0;
    int replaced = 0;
    while (idx < s.length() && replaced < count) {
      int found = s.indexOf(old, idx);
      if (found < 0) break;
      sb.append(s, idx, found);
      sb.append(newStr);
      idx = found + old.length();
      replaced++;
    }
    sb.append(s, idx, s.length());
    return Value.of(sb.toString());
  }
}
