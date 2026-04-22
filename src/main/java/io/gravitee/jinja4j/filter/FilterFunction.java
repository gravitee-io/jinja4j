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

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.value.Value;
import java.util.List;
import java.util.Map;

/**
 * A Jinja2 filter: transforms an input value given positional and keyword arguments.
 */
@FunctionalInterface
public interface FilterFunction {
  Value apply(Value input, List<Value> args, Map<String, Value> kwargs, SourceLocation loc);
}
