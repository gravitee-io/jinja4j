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
import io.gravitee.jinja4j.filter.builtin.RegexFirstFilter.RegexSupport;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Return all non-overlapping regex matches in a string as a list, Python {@code re.findall} style.
 *
 * <p>With no capture groups each element is the full match; with exactly one group each element is
 * that group; with several groups each element is a list of the groups. An unmatched group yields
 * {@code none} (unlike Python's empty string, so templates can distinguish "matched empty" from
 * "did not match"). An explicit group number can be forced with {@code regex_findall(pattern,
 * group)}. Inline flags such as {@code (?s)} are supported inside the pattern.
 */
public final class RegexFindallFilter implements NamedFilter {

  public static final RegexFindallFilter INSTANCE = new RegexFindallFilter();

  private RegexFindallFilter() {}

  @Override
  public String name() {
    return "regex_findall";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (args.isEmpty()) {
      throw new TemplateException("regex_findall() requires a pattern argument", loc);
    }
    var pattern = RegexSupport.compile(args.getFirst().asString(), loc);
    Integer forcedGroup = args.size() > 1 ? (int) args.get(1).asLong() : null;
    Matcher m = pattern.matcher(v.asString());
    List<Value> results = new ArrayList<>();
    while (m.find()) {
      if (forcedGroup != null) {
        if (forcedGroup < 0 || forcedGroup > m.groupCount()) {
          throw new TemplateException("regex_findall(): no such group " + forcedGroup, loc);
        }
        results.add(groupValue(m, forcedGroup));
      } else if (m.groupCount() == 0) {
        results.add(Value.of(m.group()));
      } else if (m.groupCount() == 1) {
        results.add(groupValue(m, 1));
      } else {
        List<Value> groups = new ArrayList<>(m.groupCount());
        for (int g = 1; g <= m.groupCount(); g++) {
          groups.add(groupValue(m, g));
        }
        results.add(Value.ofList(groups));
      }
    }
    return Value.ofList(results);
  }

  private static Value groupValue(Matcher m, int group) {
    String g = m.group(group);
    return g == null ? Value.NULL : Value.of(g);
  }
}
