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

import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.Token;
import java.util.List;

/**
 * Read/advance cursor over a token stream, shared between the statement
 * and expression parsers.
 *
 * <p>All {@code expect*} helpers throw {@link TemplateException} formatted
 * via {@link TokenDescriber} when the current token does not match, and
 * advance past the token on success.</p>
 */
public final class TokenCursor {

  private final List<Token> tokens;
  private final String templateName;
  private int pos;

  public TokenCursor(List<Token> tokens, String templateName) {
    this.tokens = tokens;
    this.templateName = templateName;
    this.pos = 0;
  }

  public String templateName() {
    return templateName;
  }

  // ---- Navigation ----

  public Token current() {
    return pos < tokens.size() ? tokens.get(pos) : tokens.getLast();
  }

  /**
   * Look at the token {@code offset} positions ahead of the cursor without
   * consuming anything. Returns the last token (EOF) when the offset runs
   * past the end of the stream.
   */
  public Token peekAhead(int offset) {
    int target = pos + offset;
    if (target < 0) return tokens.getFirst();
    return target < tokens.size() ? tokens.get(target) : tokens.getLast();
  }

  public Token advance() {
    var tok = current();
    if (pos < tokens.size() - 1) pos++;
    return tok;
  }

  /**
   * Save the current cursor position for later restoration with {@link #reset}.
   * Used for speculative lookahead that needs to consume tokens and possibly roll back.
   */
  public int mark() {
    return pos;
  }

  /** Restore a previously {@link #mark marked} position. */
  public void reset(int mark) {
    this.pos = mark;
  }

  public boolean isAtEnd() {
    return current() instanceof Token.Eof;
  }

  public boolean check(Class<? extends Token> type) {
    return type.isInstance(current());
  }

  public boolean checkIdentifier(String name) {
    return current() instanceof Token.Identifier(var n, var ignored) && n.equals(name);
  }

  // ---- Expectations ----

  public <T extends Token> T expect(Class<T> type, String expected) {
    var tok = current();
    if (!type.isInstance(tok)) {
      throw new TemplateException(
        "Expected %s but found %s".formatted(expected, TokenDescriber.describe(tok)),
        tok.location()
      );
    }
    advance();
    return type.cast(tok);
  }

  public void expectStmtEnd() {
    var tok = current();
    if (!(tok instanceof Token.StmtEnd)) {
      throw new TemplateException("Expected %%} but found %s".formatted(TokenDescriber.describe(tok)), tok.location());
    }
    advance();
  }

  public String expectIdentifierName(String what) {
    var tok = current();
    if (tok instanceof Token.Identifier(String name, var ignored)) {
      advance();
      return name;
    }
    throw new TemplateException("Expected %s but found %s".formatted(what, TokenDescriber.describe(tok)), tok.location());
  }

  public void expectIdentifier(String expected) {
    var tok = current();
    if (tok instanceof Token.Identifier(String name, var ignored) && name.equals(expected)) {
      advance();
      return;
    }
    throw new TemplateException(
      "Expected '%s' but found %s".formatted(expected, TokenDescriber.describe(tok)),
      tok.location()
    );
  }

  public <T extends Token> void expectKeyword(Class<T> type, String keyword) {
    var tok = current();
    if (type.isInstance(tok)) {
      advance();
      return;
    }
    throw new TemplateException(
      "Expected '%s' but found %s".formatted(keyword, TokenDescriber.describe(tok)),
      tok.location()
    );
  }
}
