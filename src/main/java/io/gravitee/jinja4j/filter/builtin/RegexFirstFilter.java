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
import io.gravitee.jinja4j.filter.utils.RegexSupport;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

/**
 * Return the first regex match in a string, or {@code none} when the pattern does not match.
 *
 * <p>{@code s | regex_first(pattern)} returns the full match; {@code s | regex_first(pattern,
 * group)} returns the given capture group (by number). An unmatched group yields {@code none}.
 * Inline flags such as {@code (?s)} and {@code (?i)} are supported inside the pattern.
 */
public final class RegexFirstFilter implements NamedFilter {

  public static final RegexFirstFilter INSTANCE = new RegexFirstFilter();

  private RegexFirstFilter() {}

  @Override
  public String name() {
    return "regex_first";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    if (args.isEmpty()) throw new TemplateException("regex_first() requires a pattern argument", loc);
    var pattern = RegexSupport.compile(args.getFirst().asString(), loc);
    int group = args.size() > 1 ? (int) args.get(1).asLong() : 0;
    Matcher m = pattern.matcher(v.asString());
    if (!m.find()) {
      return Value.NULL;
    }
    if (group < 0 || group > m.groupCount()) {
      throw new TemplateException("regex_first(): no such group " + group, loc);
    }
    String g = m.group(group);
    return g == null ? Value.NULL : Value.of(g);
  }
}
