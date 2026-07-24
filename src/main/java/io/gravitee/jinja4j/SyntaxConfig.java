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

/**
 * Lexical configuration for a template {@link Environment}: the tag delimiters
 * and the whitespace-control policies, mirroring Jinja2/minijinja options.
 *
 * <ul>
 *   <li>{@code trimBlocks} — remove the first newline after a block tag.</li>
 *   <li>{@code lstripBlocks} — strip inline whitespace from the start of a line
 *       up to a block tag.</li>
 *   <li>{@code keepTrailingNewline} — keep a single trailing newline at the end
 *       of a template (defaults to {@code true} to preserve historical behaviour;
 *       Jinja2's own default is {@code false}).</li>
 * </ul>
 */
public record SyntaxConfig(
  String blockStart,
  String blockEnd,
  String variableStart,
  String variableEnd,
  String commentStart,
  String commentEnd,
  boolean trimBlocks,
  boolean lstripBlocks,
  boolean keepTrailingNewline
) {
  public static final SyntaxConfig DEFAULT = new SyntaxConfig("{%", "%}", "{{", "}}", "{#", "#}", false, false, true);

  public SyntaxConfig withDelimiters(
    String blockStart,
    String blockEnd,
    String variableStart,
    String variableEnd,
    String commentStart,
    String commentEnd
  ) {
    return new SyntaxConfig(
      blockStart,
      blockEnd,
      variableStart,
      variableEnd,
      commentStart,
      commentEnd,
      trimBlocks,
      lstripBlocks,
      keepTrailingNewline
    );
  }

  public SyntaxConfig withTrimBlocks(boolean value) {
    return new SyntaxConfig(
      blockStart,
      blockEnd,
      variableStart,
      variableEnd,
      commentStart,
      commentEnd,
      value,
      lstripBlocks,
      keepTrailingNewline
    );
  }

  public SyntaxConfig withLstripBlocks(boolean value) {
    return new SyntaxConfig(
      blockStart,
      blockEnd,
      variableStart,
      variableEnd,
      commentStart,
      commentEnd,
      trimBlocks,
      value,
      keepTrailingNewline
    );
  }

  public SyntaxConfig withKeepTrailingNewline(boolean value) {
    return new SyntaxConfig(
      blockStart,
      blockEnd,
      variableStart,
      variableEnd,
      commentStart,
      commentEnd,
      trimBlocks,
      lstripBlocks,
      value
    );
  }
}
