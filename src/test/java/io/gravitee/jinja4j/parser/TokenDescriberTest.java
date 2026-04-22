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
import io.gravitee.jinja4j.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers every arm of {@link TokenDescriber#describe(Token)}, including
 * the {@code null} branch and the {@code default} arm (operator/punctuation
 * tokens that are not handled explicitly).
 */
class TokenDescriberTest {

  private static final SourceLocation LOC = SourceLocation.UNKNOWN;

  @Nested
  @DisplayName("Literals and structural tokens are formatted with their payload")
  class WithPayload {

    @Test
    void textTokenIsQuotedAndPreservedWhenShort() {
      assertThat(TokenDescriber.describe(new Token.Text("hello", LOC))).isEqualTo("text 'hello'");
    }

    @Test
    void textTokenIsTruncatedWhenLongerThanTwentyCharacters() {
      // "abcdefghijklmnopqrstuvwxyz" is 26 chars; the first 20 are kept,
      // followed by an ellipsis.
      assertThat(TokenDescriber.describe(new Token.Text("abcdefghijklmnopqrstuvwxyz", LOC))).isEqualTo(
        "text 'abcdefghijklmnopqrst...'"
      );
    }

    @Test
    void textTokenAtExactlyTwentyCharactersIsNotTruncated() {
      var twenty = "12345678901234567890"; // 20
      assertThat(TokenDescriber.describe(new Token.Text(twenty, LOC))).isEqualTo("text '" + twenty + "'");
    }

    @Test
    void identifierToken() {
      assertThat(TokenDescriber.describe(new Token.Identifier("user_name", LOC))).isEqualTo("identifier 'user_name'");
    }

    @Test
    void stringLiteralIsQuoted() {
      assertThat(TokenDescriber.describe(new Token.StringLiteral("hi", LOC))).isEqualTo("string 'hi'");
    }

    @Test
    void integerLiteral() {
      assertThat(TokenDescriber.describe(new Token.IntegerLiteral(42L, LOC))).isEqualTo("integer 42");
    }

    @Test
    void floatLiteralUsesPercentF() {
      // %f default gives six fractional digits.
      assertThat(TokenDescriber.describe(new Token.FloatLiteral(3.14, LOC))).startsWith("float 3.14");
    }

    @Test
    void booleanTrueAndFalse() {
      assertThat(TokenDescriber.describe(new Token.BooleanLiteral(true, LOC))).isEqualTo("true");
      assertThat(TokenDescriber.describe(new Token.BooleanLiteral(false, LOC))).isEqualTo("false");
    }

    @Test
    void noneLiteral() {
      assertThat(TokenDescriber.describe(new Token.NoneLiteral(LOC))).isEqualTo("none");
    }
  }

  @Nested
  @DisplayName("Tag delimiters and EOF are rendered as their source-form")
  class Delimiters {

    @Test
    void exprStart() {
      assertThat(TokenDescriber.describe(new Token.ExprStart(false, LOC))).isEqualTo("{{");
    }

    @Test
    void exprEnd() {
      assertThat(TokenDescriber.describe(new Token.ExprEnd(false, LOC))).isEqualTo("}}");
    }

    @Test
    void stmtStart() {
      assertThat(TokenDescriber.describe(new Token.StmtStart(false, LOC))).isEqualTo("{%");
    }

    @Test
    void stmtEnd() {
      assertThat(TokenDescriber.describe(new Token.StmtEnd(false, LOC))).isEqualTo("%}");
    }

    @Test
    void eof() {
      assertThat(TokenDescriber.describe(new Token.Eof(LOC))).isEqualTo("end of file");
    }

    @Test
    void trimFlagsDoNotLeakIntoTheDescription() {
      // The trim hints on delimiters are an internal concern; they must
      // not affect how the token is rendered to the user.
      assertThat(TokenDescriber.describe(new Token.ExprStart(true, LOC))).isEqualTo("{{");
      assertThat(TokenDescriber.describe(new Token.StmtEnd(true, LOC))).isEqualTo("%}");
    }
  }

  @Nested
  @DisplayName("Operator and punctuation tokens fall through to the default arm")
  class DefaultArm {

    @Test
    void arithmeticOperators() {
      assertThat(TokenDescriber.describe(new Token.Plus(LOC))).isEqualTo("Plus");
      assertThat(TokenDescriber.describe(new Token.Minus(LOC))).isEqualTo("Minus");
      assertThat(TokenDescriber.describe(new Token.Star(LOC))).isEqualTo("Star");
      assertThat(TokenDescriber.describe(new Token.Slash(LOC))).isEqualTo("Slash");
      assertThat(TokenDescriber.describe(new Token.FloorDiv(LOC))).isEqualTo("FloorDiv");
      assertThat(TokenDescriber.describe(new Token.Percent(LOC))).isEqualTo("Percent");
      assertThat(TokenDescriber.describe(new Token.Power(LOC))).isEqualTo("Power");
      assertThat(TokenDescriber.describe(new Token.Tilde(LOC))).isEqualTo("Tilde");
    }

    @Test
    void comparisonAndAssignOperators() {
      assertThat(TokenDescriber.describe(new Token.Assign(LOC))).isEqualTo("Assign");
      assertThat(TokenDescriber.describe(new Token.Eq(LOC))).isEqualTo("Eq");
      assertThat(TokenDescriber.describe(new Token.Ne(LOC))).isEqualTo("Ne");
      assertThat(TokenDescriber.describe(new Token.Lt(LOC))).isEqualTo("Lt");
      assertThat(TokenDescriber.describe(new Token.Gt(LOC))).isEqualTo("Gt");
      assertThat(TokenDescriber.describe(new Token.Le(LOC))).isEqualTo("Le");
      assertThat(TokenDescriber.describe(new Token.Ge(LOC))).isEqualTo("Ge");
    }

    @Test
    void punctuationTokens() {
      assertThat(TokenDescriber.describe(new Token.Dot(LOC))).isEqualTo("Dot");
      assertThat(TokenDescriber.describe(new Token.Comma(LOC))).isEqualTo("Comma");
      assertThat(TokenDescriber.describe(new Token.Colon(LOC))).isEqualTo("Colon");
      assertThat(TokenDescriber.describe(new Token.Pipe(LOC))).isEqualTo("Pipe");
    }

    @Test
    void bracketTokens() {
      assertThat(TokenDescriber.describe(new Token.LParen(LOC))).isEqualTo("LParen");
      assertThat(TokenDescriber.describe(new Token.RParen(LOC))).isEqualTo("RParen");
      assertThat(TokenDescriber.describe(new Token.LBracket(LOC))).isEqualTo("LBracket");
      assertThat(TokenDescriber.describe(new Token.RBracket(LOC))).isEqualTo("RBracket");
      assertThat(TokenDescriber.describe(new Token.LBrace(LOC))).isEqualTo("LBrace");
      assertThat(TokenDescriber.describe(new Token.RBrace(LOC))).isEqualTo("RBrace");
    }

    @Test
    void keywordTokens() {
      assertThat(TokenDescriber.describe(new Token.And(LOC))).isEqualTo("And");
      assertThat(TokenDescriber.describe(new Token.Or(LOC))).isEqualTo("Or");
      assertThat(TokenDescriber.describe(new Token.Not(LOC))).isEqualTo("Not");
      assertThat(TokenDescriber.describe(new Token.In(LOC))).isEqualTo("In");
      assertThat(TokenDescriber.describe(new Token.Is(LOC))).isEqualTo("Is");
      assertThat(TokenDescriber.describe(new Token.If(LOC))).isEqualTo("If");
      assertThat(TokenDescriber.describe(new Token.Else(LOC))).isEqualTo("Else");
      assertThat(TokenDescriber.describe(new Token.Elif(LOC))).isEqualTo("Elif");
    }

    @Test
    void commentDelimitersFallThroughToDefault() {
      // CommentStart and CommentEnd are not part of the explicit delimiter
      // cases (the parser never produces error messages against them), so
      // they hit the default arm too.
      assertThat(TokenDescriber.describe(new Token.CommentStart(false, LOC))).isEqualTo("CommentStart");
      assertThat(TokenDescriber.describe(new Token.CommentEnd(false, LOC))).isEqualTo("CommentEnd");
    }
  }

  @Test
  @DisplayName("null token renders as the literal string 'null'")
  void nullToken() {
    assertThat(TokenDescriber.describe(null)).isEqualTo("null");
  }
}
