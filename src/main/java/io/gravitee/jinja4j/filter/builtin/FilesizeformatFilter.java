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

/** Format a byte count as a human-readable file size (decimal or binary). */
public final class FilesizeformatFilter implements NamedFilter {

  public static final FilesizeformatFilter INSTANCE = new FilesizeformatFilter();

  private FilesizeformatFilter() {}

  @Override
  public String name() {
    return "filesizeformat";
  }

  @Override
  public Value apply(Value v, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    long bytes = v.asLong();
    boolean binary = !args.isEmpty() && args.getFirst().isTruthy();
    var units = binary
      ? new String[] { "Bytes", "KiB", "MiB", "GiB", "TiB" }
      : new String[] { "Bytes", "kB", "MB", "GB", "TB" };
    double base = binary ? 1024.0 : 1000.0;
    double size = bytes;
    int unit = 0;
    while (size >= base && unit < units.length - 1) {
      size /= base;
      unit++;
    }
    if (unit == 0) return Value.of("%d %s".formatted((long) size, units[0]));
    return Value.of("%.1f %s".formatted(size, units[unit]));
  }
}
