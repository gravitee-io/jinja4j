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

/**
 * Methods exposed as attributes on list values.
 * Unknown names resolve to {@link Value#UNDEFINED}.
 */
public final class ListMethods {

  private ListMethods() {}

  public static Value lookup(List<Value> items, String attribute, SourceLocation loc) {
    return switch (attribute) {
      case "append" -> callable("list.append", (args, kw) -> {
        if (args.isEmpty()) return Value.ofList(items);
        var mutable = new ArrayList<>(items);
        mutable.add(args.getFirst());
        return Value.ofList(mutable);
      });
      case "length" -> Value.of(items.size());
      default -> Value.UNDEFINED;
    };
  }

  private static Value callable(String name, TemplateFunction fn) {
    return Value.ofCallable(name, fn);
  }
}
