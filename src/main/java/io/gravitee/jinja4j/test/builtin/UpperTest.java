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

/** Tests whether a string is already uppercase. */
public final class UpperTest implements NamedTest {

  public static final UpperTest INSTANCE = new UpperTest();

  private UpperTest() {}

  @Override
  public String name() {
    return "upper";
  }

  @Override
  public boolean test(Value value, List<Value> args, SourceLocation loc) {
    return value.isString() && value.asString().equals(value.asString().toUpperCase());
  }
}
