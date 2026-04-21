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
 * Token types produced by the Jinja2 lexer.
 */
public sealed interface Token {
  SourceLocation location();

  // Raw text between tags
  record Text(String value, SourceLocation location) implements Token {}

  // {{ ... }} expression output
  record ExprStart(boolean trimLeft, SourceLocation location) implements Token {}

  record ExprEnd(boolean trimRight, SourceLocation location) implements Token {}

  // {% ... %} statement tags
  record StmtStart(boolean trimLeft, SourceLocation location) implements Token {}

  record StmtEnd(boolean trimRight, SourceLocation location) implements Token {}

  // {# ... #} comments
  record CommentStart(boolean trimLeft, SourceLocation location) implements Token {}

  record CommentEnd(boolean trimRight, SourceLocation location) implements Token {}

  // Literals
  record IntegerLiteral(long value, SourceLocation location) implements Token {}

  record FloatLiteral(double value, SourceLocation location) implements Token {}

  record StringLiteral(String value, SourceLocation location) implements Token {}

  record BooleanLiteral(boolean value, SourceLocation location) implements Token {}

  record NoneLiteral(SourceLocation location) implements Token {}

  // Identifier
  record Identifier(String name, SourceLocation location) implements Token {}

  // Operators
  record Plus(SourceLocation location) implements Token {}

  record Minus(SourceLocation location) implements Token {}

  record Star(SourceLocation location) implements Token {}

  record Slash(SourceLocation location) implements Token {}

  record FloorDiv(SourceLocation location) implements Token {}

  record Percent(SourceLocation location) implements Token {}

  record Power(SourceLocation location) implements Token {}

  record Tilde(SourceLocation location) implements Token {}

  record Assign(SourceLocation location) implements Token {}

  record Eq(SourceLocation location) implements Token {}

  record Ne(SourceLocation location) implements Token {}

  record Lt(SourceLocation location) implements Token {}

  record Gt(SourceLocation location) implements Token {}

  record Le(SourceLocation location) implements Token {}

  record Ge(SourceLocation location) implements Token {}

  record Dot(SourceLocation location) implements Token {}

  record Comma(SourceLocation location) implements Token {}

  record Colon(SourceLocation location) implements Token {}

  record Pipe(SourceLocation location) implements Token {}

  record LParen(SourceLocation location) implements Token {}

  record RParen(SourceLocation location) implements Token {}

  record LBracket(SourceLocation location) implements Token {}

  record RBracket(SourceLocation location) implements Token {}

  record LBrace(SourceLocation location) implements Token {}

  record RBrace(SourceLocation location) implements Token {}

  // Keywords (handled as identifiers by the lexer, resolved by the parser)
  record And(SourceLocation location) implements Token {}

  record Or(SourceLocation location) implements Token {}

  record Not(SourceLocation location) implements Token {}

  record In(SourceLocation location) implements Token {}

  record Is(SourceLocation location) implements Token {}

  record If(SourceLocation location) implements Token {}

  record Else(SourceLocation location) implements Token {}

  record Elif(SourceLocation location) implements Token {}

  // End of file
  record Eof(SourceLocation location) implements Token {}
}
