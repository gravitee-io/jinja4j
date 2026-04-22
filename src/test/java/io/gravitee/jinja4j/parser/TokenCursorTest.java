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

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.Token;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link TokenCursor}, targeting the full public
 * API and every error-throw path of the {@code expect*} helpers. Hand-built
 * token lists replace the need to craft malformed templates to provoke
 * each branch.
 */
class TokenCursorTest {

  private static final SourceLocation LOC = SourceLocation.UNKNOWN;

  private static TokenCursor cursorOf(Token... tokens) {
    return new TokenCursor(List.of(tokens), "test.j2");
  }

  private static Token.Identifier id(String name) {
    return new Token.Identifier(name, LOC);
  }

  private static Token.Eof eof() {
    return new Token.Eof(LOC);
  }

  // ---- templateName ----

  @Test
  void templateNameReturnsTheNameProvidedAtConstruction() {
    var cursor = new TokenCursor(List.of(eof()), "hello.j2");
    assertThat(cursor.templateName()).isEqualTo("hello.j2");
  }

  // ---- Navigation ----

  @Nested
  @DisplayName("current() and advance()")
  class Navigation {

    @Test
    void currentReturnsTheTokenAtPosition() {
      var cursor = cursorOf(id("a"), id("b"), eof());
      assertThat(cursor.current()).isEqualTo(id("a"));
    }

    @Test
    void advanceReturnsTheCurrentTokenAndMovesForward() {
      var cursor = cursorOf(id("a"), id("b"), eof());
      assertThat(cursor.advance()).isEqualTo(id("a"));
      assertThat(cursor.current()).isEqualTo(id("b"));
    }

    @Test
    void advanceStopsAtLastTokenAndCurrentKeepsReturningIt() {
      var cursor = cursorOf(id("a"), eof());
      cursor.advance(); // on eof
      cursor.advance(); // still on eof, should not go past
      cursor.advance(); // idem
      // Even with pos >= tokens.size() the getLast() fallback kicks in
      // (exercises the false branch of 'pos < tokens.size()' at L49).
      assertThat(cursor.current()).isEqualTo(eof());
    }

    @Test
    void currentFallsBackToLastTokenWhenPositionIsOutOfBounds() {
      // advance() clamps at tokens.size() - 1, but a stale mark restored
      // with reset() can push pos outside the bounds. The getLast()
      // fallback at current() keeps the cursor well-defined in that case.
      var cursor = cursorOf(id("a"), id("b"), eof());
      cursor.reset(99);
      assertThat(cursor.current()).isEqualTo(eof());
    }
  }

  // ---- peekAhead ----

  @Nested
  @DisplayName("peekAhead(offset)")
  class PeekAhead {

    @Test
    void positiveOffsetInsideStream() {
      var cursor = cursorOf(id("a"), id("b"), id("c"), eof());
      assertThat(cursor.peekAhead(1)).isEqualTo(id("b"));
      assertThat(cursor.peekAhead(2)).isEqualTo(id("c"));
    }

    @Test
    void zeroOffsetEqualsCurrent() {
      var cursor = cursorOf(id("a"), id("b"), eof());
      assertThat(cursor.peekAhead(0)).isEqualTo(cursor.current());
    }

    @Test
    void negativeOffsetClampsToFirstToken() {
      var cursor = cursorOf(id("a"), id("b"), eof());
      cursor.advance(); // now positioned on 'b', peekAhead(-5) would be pos=-4
      assertThat(cursor.peekAhead(-5)).isEqualTo(id("a"));
    }

    @Test
    void overshootingOffsetClampsToLastToken() {
      var cursor = cursorOf(id("a"), eof());
      assertThat(cursor.peekAhead(100)).isEqualTo(eof());
    }
  }

  // ---- mark / reset ----

  @Test
  @DisplayName("mark() and reset() support speculative lookahead")
  void markAndResetRoundtripsPosition() {
    var cursor = cursorOf(id("a"), id("b"), id("c"), eof());
    int saved = cursor.mark();
    cursor.advance();
    cursor.advance();
    assertThat(cursor.current()).isEqualTo(id("c"));
    cursor.reset(saved);
    assertThat(cursor.current()).isEqualTo(id("a"));
  }

  // ---- isAtEnd ----

  @Nested
  @DisplayName("isAtEnd()")
  class IsAtEnd {

    @Test
    void falseBeforeEof() {
      var cursor = cursorOf(id("a"), eof());
      assertThat(cursor.isAtEnd()).isFalse();
    }

    @Test
    void trueWhenCurrentIsEof() {
      var cursor = cursorOf(id("a"), eof());
      cursor.advance();
      assertThat(cursor.isAtEnd()).isTrue();
    }
  }

  // ---- check ----

  @Nested
  @DisplayName("check(Class)")
  class Check {

    @Test
    void matchingType() {
      var cursor = cursorOf(id("foo"), eof());
      assertThat(cursor.check(Token.Identifier.class)).isTrue();
    }

    @Test
    void nonMatchingType() {
      var cursor = cursorOf(id("foo"), eof());
      assertThat(cursor.check(Token.Eof.class)).isFalse();
    }
  }

  // ---- checkIdentifier ----

  @Nested
  @DisplayName("checkIdentifier(name)")
  class CheckIdentifier {

    @Test
    void matchingName() {
      var cursor = cursorOf(id("set"), eof());
      assertThat(cursor.checkIdentifier("set")).isTrue();
    }

    @Test
    void identifierWithDifferentName() {
      var cursor = cursorOf(id("set"), eof());
      assertThat(cursor.checkIdentifier("for")).isFalse();
    }

    @Test
    void nonIdentifierToken() {
      var cursor = cursorOf(eof());
      assertThat(cursor.checkIdentifier("anything")).isFalse();
    }
  }

  // ---- expect ----

  @Nested
  @DisplayName("expect(Class, label)")
  class Expect {

    @Test
    void returnsTheTokenAndAdvancesOnMatch() {
      var cursor = cursorOf(id("a"), id("b"), eof());
      var token = cursor.expect(Token.Identifier.class, "identifier");
      assertThat(token).isEqualTo(id("a"));
      assertThat(cursor.current()).isEqualTo(id("b"));
    }

    @Test
    void throwsOnMismatchWithDescriptiveMessage() {
      var cursor = cursorOf(new Token.Plus(LOC), eof());
      assertThatThrownBy(() -> cursor.expect(Token.Identifier.class, "identifier"))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Expected identifier but found")
        .hasMessageContaining("Plus"); // via TokenDescriber default arm
    }
  }

  // ---- expectStmtEnd ----

  @Nested
  @DisplayName("expectStmtEnd()")
  class ExpectStmtEnd {

    @Test
    void advancesOnStmtEnd() {
      var cursor = cursorOf(new Token.StmtEnd(false, LOC), id("next"), eof());
      cursor.expectStmtEnd();
      assertThat(cursor.current()).isEqualTo(id("next"));
    }

    @Test
    void throwsOnAnythingElse() {
      var cursor = cursorOf(id("notStmtEnd"), eof());
      assertThatThrownBy(cursor::expectStmtEnd)
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Expected %} but found")
        .hasMessageContaining("identifier 'notStmtEnd'");
    }
  }

  // ---- expectIdentifierName ----

  @Nested
  @DisplayName("expectIdentifierName(what)")
  class ExpectIdentifierName {

    @Test
    void returnsTheNameAndAdvances() {
      var cursor = cursorOf(id("x"), id("y"), eof());
      assertThat(cursor.expectIdentifierName("variable name")).isEqualTo("x");
      assertThat(cursor.current()).isEqualTo(id("y"));
    }

    @Test
    void throwsOnNonIdentifier() {
      var cursor = cursorOf(new Token.IntegerLiteral(42, LOC), eof());
      assertThatThrownBy(() -> cursor.expectIdentifierName("loop variable"))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Expected loop variable but found")
        .hasMessageContaining("integer 42");
    }
  }

  // ---- expectIdentifier ----

  @Nested
  @DisplayName("expectIdentifier(expected)")
  class ExpectIdentifier {

    @Test
    void advancesOnMatchingName() {
      var cursor = cursorOf(id("endif"), eof());
      cursor.expectIdentifier("endif");
      assertThat(cursor.isAtEnd()).isTrue();
    }

    @Test
    void throwsOnIdentifierWithWrongName() {
      // Right token type, wrong name: exercises the 'name.equals(expected)' false branch.
      var cursor = cursorOf(id("endfor"), eof());
      assertThatThrownBy(() -> cursor.expectIdentifier("endif"))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Expected 'endif' but found")
        .hasMessageContaining("identifier 'endfor'");
    }

    @Test
    void throwsOnNonIdentifierToken() {
      // Wrong token type: exercises the 'instanceof Identifier' false branch.
      var cursor = cursorOf(new Token.StmtEnd(false, LOC), eof());
      assertThatThrownBy(() -> cursor.expectIdentifier("endif"))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Expected 'endif' but found")
        .hasMessageContaining("%}");
    }
  }

  // ---- expectKeyword ----

  @Nested
  @DisplayName("expectKeyword(Class, keyword)")
  class ExpectKeyword {

    @Test
    void advancesOnMatchingType() {
      var cursor = cursorOf(new Token.In(LOC), id("rest"), eof());
      cursor.expectKeyword(Token.In.class, "in");
      assertThat(cursor.current()).isEqualTo(id("rest"));
    }

    @Test
    void throwsOnMismatch() {
      var cursor = cursorOf(id("notKeyword"), eof());
      assertThatThrownBy(() -> cursor.expectKeyword(Token.In.class, "in"))
        .isInstanceOf(TemplateException.class)
        .hasMessageContaining("Expected 'in' but found")
        .hasMessageContaining("identifier 'notKeyword'");
    }
  }
}
