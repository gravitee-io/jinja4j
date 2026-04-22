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
import java.util.stream.Collectors;

/** Join list items into a string with an optional separator and attribute extraction. */
public final class JoinFilter implements NamedFilter {

  public static final JoinFilter INSTANCE = new JoinFilter();

  private JoinFilter() {}

  @Override
  public String name() {
    return "join";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    var sep = !args.isEmpty() ? args.getFirst().asString() : "";
    if (v instanceof Value.ListVal lv) {
      var attrName = kwargs.containsKey("attribute") ? kwargs.get("attribute").asString() : null;
      return Value.of(
        lv
          .items()
          .stream()
          .map(item -> {
            if (attrName != null && item instanceof Value.MapVal mv) {
              var attrVal = mv.entries().get(attrName);
              return attrVal != null ? attrVal.asString() : "";
            }
            return item.asString();
          })
          .collect(Collectors.joining(sep))
      );
    }
    return v;
  }
}
