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
package io.gravitee.jinja4j.parser;

import io.gravitee.jinja4j.Token;

/**
 * Formats a {@link Token} into a human-readable fragment used inside
 * parser error messages (e.g. {@code "Expected %} but found
 * identifier 'foo'"}).
 *
 * <p>Long text tokens are truncated so that error messages stay readable.</p>
 */
public final class TokenDescriber {

  private static final int MAX_TEXT_LENGTH = 20;

  private TokenDescriber() {}

  public static String describe(Token tok) {
    return switch (tok) {
      case null -> "null";
      case Token.Text(String value, var ignored) -> "text '%s'".formatted(truncate(value));
      case Token.Identifier(String name, var ignored) -> "identifier '%s'".formatted(name);
      case Token.StringLiteral(String value, var ignored) -> "string '%s'".formatted(value);
      case Token.IntegerLiteral(long value, var ignored) -> "integer %d".formatted(value);
      case Token.FloatLiteral(double value, var ignored) -> "float %f".formatted(value);
      case Token.BooleanLiteral(boolean value, var ignored) -> value ? "true" : "false";
      case Token.NoneLiteral ignored -> "none";
      case Token.Eof ignored -> "end of file";
      case Token.StmtEnd ignored -> "%}";
      case Token.ExprEnd ignored -> "}}";
      case Token.StmtStart ignored -> "{%";
      case Token.ExprStart ignored -> "{{";
      default -> tok.getClass().getSimpleName();
    };
  }

  private static String truncate(String s) {
    return s.length() > MAX_TEXT_LENGTH ? s.substring(0, MAX_TEXT_LENGTH) + "..." : s;
  }
}
