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
package io.gravitee.jinja4j.test.builtin;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.test.NamedTest;
import io.gravitee.jinja4j.value.Value;
import java.util.List;

/** Less-than-or-equal test. */
public final class LeTest implements NamedTest {

  public static final LeTest INSTANCE = new LeTest();

  private LeTest() {}

  @Override
  public String name() {
    return "le";
  }

  @Override
  public boolean test(Value value, List<Value> args, SourceLocation loc) {
    if (args.isEmpty()) return false;
    return value.asDouble() <= args.getFirst().asDouble();
  }
}
