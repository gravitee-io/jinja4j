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
package io.gravitee.jinja4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent context builder for template rendering.
 *
 * <pre>{@code
 * var ctx = Context.of("name", "World").and("count", 42);
 * var result = template.render(ctx);
 * }</pre>
 */
public final class Context {

  private final Map<String, Object> data = new LinkedHashMap<>();

  private Context() {}

  public static Context of(String key, Object value) {
    var ctx = new Context();
    ctx.data.put(key, value);
    return ctx;
  }

  public Context and(String key, Object value) {
    data.put(key, value);
    return this;
  }

  public Map<String, Object> toMap() {
    return Map.copyOf(data);
  }
}
