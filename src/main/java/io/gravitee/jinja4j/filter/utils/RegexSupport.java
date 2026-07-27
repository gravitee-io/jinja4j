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
package io.gravitee.jinja4j.filter.utils;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import java.util.regex.PatternSyntaxException;

/** Shared compiled-pattern cache for the regex filters. */
public final class RegexSupport {

  private static final java.util.concurrent.ConcurrentHashMap<String, java.util.regex.Pattern> CACHE =
    new java.util.concurrent.ConcurrentHashMap<>();

  private RegexSupport() {}

  public static java.util.regex.Pattern compile(String pattern, SourceLocation loc) {
    try {
      return CACHE.computeIfAbsent(pattern, java.util.regex.Pattern::compile);
    } catch (PatternSyntaxException e) {
      throw new TemplateException("invalid regex pattern: " + e.getMessage(), loc);
    }
  }
}
