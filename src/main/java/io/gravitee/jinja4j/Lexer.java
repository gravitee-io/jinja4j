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

import java.util.*;

/**
 * Lexer (tokenizer) for Jinja2 template syntax.
 * Splits raw template source into a stream of {@link Token}s.
 *
 * The lexer operates in two modes:
 * - TEXT mode: scanning for tag openers ({%, {{, {#)
 * - TAG mode: tokenizing expressions and statements inside tags
 */
public final class Lexer {

  private final String source;
  private final String templateName;
  private final SyntaxConfig config;
  private int pos;
  private int line;
  private int col;
  private final List<Token> tokens = new ArrayList<>();
  private boolean inTag = false;

  // Keywords map
  private static final Map<String, java.util.function.Function<SourceLocation, Token>> KEYWORDS = Map.ofEntries(
    Map.entry("true", loc -> new Token.BooleanLiteral(true, loc)),
    Map.entry("True", loc -> new Token.BooleanLiteral(true, loc)),
    Map.entry("false", loc -> new Token.BooleanLiteral(false, loc)),
    Map.entry("False", loc -> new Token.BooleanLiteral(false, loc)),
    Map.entry("none", Token.NoneLiteral::new),
    Map.entry("None", Token.NoneLiteral::new),
    Map.entry("and", Token.And::new),
    Map.entry("or", Token.Or::new),
    Map.entry("not", Token.Not::new),
    Map.entry("in", Token.In::new),
    Map.entry("is", Token.Is::new),
    Map.entry("if", Token.If::new),
    Map.entry("else", Token.Else::new),
    Map.entry("elif", Token.Elif::new)
  );

  public Lexer(String source, String templateName) {
    this(source, templateName, SyntaxConfig.DEFAULT);
  }

  public Lexer(String source, String templateName, SyntaxConfig config) {
    this.source = source;
    this.templateName = templateName;
    this.config = config;
    this.pos = 0;
    this.line = 1;
    this.col = 1;
  }

  public List<Token> tokenize() {
    while (pos < source.length()) {
      if (!inTag) {
        scanText();
      } else {
        scanTag();
      }
    }
    tokens.add(new Token.Eof(loc()));
    return Collections.unmodifiableList(tokens);
  }

  private SourceLocation loc() {
    return new SourceLocation(templateName, line, col);
  }

  private char peek() {
    return pos < source.length() ? source.charAt(pos) : '\0';
  }

  private char peek(int offset) {
    int idx = pos + offset;
    return idx < source.length() ? source.charAt(idx) : '\0';
  }

  private char advance() {
    char c = source.charAt(pos++);
    if (c == '\n') {
      line++;
      col = 1;
    } else {
      col++;
    }
    return c;
  }

  private boolean match(String s) {
    if (source.startsWith(s, pos)) {
      for (int i = 0; i < s.length(); i++) advance();
      return true;
    }
    return false;
  }

  private void scanText() {
    var sb = new StringBuilder();
    var startLoc = loc();

    while (pos < source.length()) {
      if (
        source.startsWith(config.blockStart(), pos) ||
        source.startsWith(config.variableStart(), pos) ||
        source.startsWith(config.commentStart(), pos)
      ) {
        break;
      }
      sb.append(advance());
    }

    if (!sb.isEmpty()) {
      tokens.add(new Token.Text(sb.toString(), startLoc));
    }

    // Now handle the tag opener if present
    if (pos < source.length()) {
      var tagLoc = loc();
      if (source.startsWith(config.commentStart(), pos)) {
        // Comment — skip until comment end
        match(config.commentStart());
        boolean trimLeft = peek() == '-';
        if (trimLeft) advance();
        // Find closing delimiter
        while (pos < source.length()) {
          if (
            (peek() == '-' && source.startsWith(config.commentEnd(), pos + 1)) || source.startsWith(config.commentEnd(), pos)
          ) {
            boolean trimRight = peek() == '-';
            if (trimRight) advance();
            match(config.commentEnd());
            // Apply whitespace trimming
            if (trimLeft) {
              trimLastTextRight();
            }
            if (trimRight) {
              // Next text token should be left-trimmed — we'll handle it when we encounter the text
              trimNextTextLeft = true;
            }
            return;
          }
          advance();
        }
        throw new TemplateException("Unclosed comment", tagLoc);
      } else if (source.startsWith(config.variableStart(), pos)) {
        match(config.variableStart());
        boolean trimLeft = peek() == '-';
        if (trimLeft) {
          advance();
          trimLastTextRight();
        }
        tokens.add(new Token.ExprStart(trimLeft, tagLoc));
        inTag = true;
        currentTagType = TagType.EXPR;
      } else if (source.startsWith(config.blockStart(), pos)) {
        match(config.blockStart());
        boolean trimLeft = peek() == '-';
        if (trimLeft) {
          advance();
          trimLastTextRight();
        }
        tokens.add(new Token.StmtStart(trimLeft, tagLoc));
        inTag = true;
        currentTagType = TagType.STMT;
      }
    }
  }

  private boolean trimNextTextLeft = false;

  private void trimLastTextRight() {
    // Trim trailing whitespace from the last Text token
    if (!tokens.isEmpty() && tokens.getLast() instanceof Token.Text(var val, var loc)) {
      var trimmed = rtrim(val);
      tokens.set(tokens.size() - 1, new Token.Text(trimmed, loc));
    }
  }

  private String rtrim(String s) {
    int end = s.length();
    while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
    return s.substring(0, end);
  }

  private enum TagType {
    EXPR,
    STMT,
  }

  private TagType currentTagType = null;

  private void scanTag() {
    skipWhitespaceInTag();

    if (pos >= source.length()) {
      throw new TemplateException("Unclosed tag", loc());
    }

    // Check for tag close
    if (currentTagType == TagType.EXPR) {
      if (peek() == '-' && source.startsWith(config.variableEnd(), pos + 1)) {
        advance(); // skip -
        match(config.variableEnd());
        tokens.add(new Token.ExprEnd(true, loc()));
        inTag = false;
        currentTagType = null;
        trimNextTextLeft = true;
        return;
      }
      if (source.startsWith(config.variableEnd(), pos)) {
        match(config.variableEnd());
        tokens.add(new Token.ExprEnd(false, loc()));
        inTag = false;
        currentTagType = null;
        return;
      }
    } else if (currentTagType == TagType.STMT) {
      if (peek() == '-' && source.startsWith(config.blockEnd(), pos + 1)) {
        advance(); // skip -
        match(config.blockEnd());
        tokens.add(new Token.StmtEnd(true, loc()));
        inTag = false;
        currentTagType = null;
        trimNextTextLeft = true;
        return;
      }
      if (source.startsWith(config.blockEnd(), pos)) {
        match(config.blockEnd());
        tokens.add(new Token.StmtEnd(false, loc()));
        inTag = false;
        currentTagType = null;
        return;
      }
    }

    var tokLoc = loc();
    char c = peek();

    // String literals
    if (c == '"' || c == '\'') {
      scanString(c, tokLoc);
      return;
    }

    // Numbers
    if (Character.isDigit(c)) {
      scanNumber(tokLoc);
      return;
    }

    // Identifiers and keywords
    if (Character.isLetter(c) || c == '_') {
      scanIdentifier(tokLoc);
      return;
    }

    // Two-character operators
    if (c == '=' && peek(1) == '=') {
      advance();
      advance();
      tokens.add(new Token.Eq(tokLoc));
      return;
    }
    if (c == '!' && peek(1) == '=') {
      advance();
      advance();
      tokens.add(new Token.Ne(tokLoc));
      return;
    }
    if (c == '<' && peek(1) == '=') {
      advance();
      advance();
      tokens.add(new Token.Le(tokLoc));
      return;
    }
    if (c == '>' && peek(1) == '=') {
      advance();
      advance();
      tokens.add(new Token.Ge(tokLoc));
      return;
    }
    if (c == '/' && peek(1) == '/') {
      advance();
      advance();
      tokens.add(new Token.FloorDiv(tokLoc));
      return;
    }
    if (c == '*' && peek(1) == '*') {
      advance();
      advance();
      tokens.add(new Token.Power(tokLoc));
      return;
    }

    // Single-character operators/punctuation
    advance();
    switch (c) {
      case '+' -> tokens.add(new Token.Plus(tokLoc));
      case '-' -> tokens.add(new Token.Minus(tokLoc));
      case '*' -> tokens.add(new Token.Star(tokLoc));
      case '/' -> tokens.add(new Token.Slash(tokLoc));
      case '%' -> tokens.add(new Token.Percent(tokLoc));
      case '~' -> tokens.add(new Token.Tilde(tokLoc));
      case '=' -> tokens.add(new Token.Assign(tokLoc));
      case '<' -> tokens.add(new Token.Lt(tokLoc));
      case '>' -> tokens.add(new Token.Gt(tokLoc));
      case '.' -> tokens.add(new Token.Dot(tokLoc));
      case ',' -> tokens.add(new Token.Comma(tokLoc));
      case ':' -> tokens.add(new Token.Colon(tokLoc));
      case '|' -> tokens.add(new Token.Pipe(tokLoc));
      case '(' -> tokens.add(new Token.LParen(tokLoc));
      case ')' -> tokens.add(new Token.RParen(tokLoc));
      case '[' -> tokens.add(new Token.LBracket(tokLoc));
      case ']' -> tokens.add(new Token.RBracket(tokLoc));
      case '{' -> tokens.add(new Token.LBrace(tokLoc));
      case '}' -> tokens.add(new Token.RBrace(tokLoc));
      default -> throw new TemplateException("Unexpected character '%c'".formatted(c), tokLoc);
    }
  }

  private void skipWhitespaceInTag() {
    while (pos < source.length() && Character.isWhitespace(peek())) {
      advance();
    }
  }

  private void scanString(char quote, SourceLocation loc) {
    advance(); // skip opening quote
    var sb = new StringBuilder();
    while (pos < source.length() && peek() != quote) {
      char c = advance();
      if (c == '\\') {
        if (pos < source.length()) {
          char next = advance();
          switch (next) {
            case 'n' -> sb.append('\n');
            case 't' -> sb.append('\t');
            case 'r' -> sb.append('\r');
            case '\\' -> sb.append('\\');
            case '\'' -> sb.append('\'');
            case '"' -> sb.append('"');
            default -> {
              sb.append('\\');
              sb.append(next);
            }
          }
        }
      } else {
        sb.append(c);
      }
    }
    if (pos >= source.length()) {
      throw new TemplateException("Unterminated string literal", loc);
    }
    advance(); // skip closing quote
    tokens.add(new Token.StringLiteral(sb.toString(), loc));
  }

  private void scanNumber(SourceLocation loc) {
    var sb = new StringBuilder();
    while (pos < source.length() && Character.isDigit(peek())) {
      sb.append(advance());
    }
    // A number immediately following a '.' is a dotted lookup segment
    // (e.g. foo.0.1) and must be an integer, not a float — the '.' is the
    // accessor, not a decimal point.
    if (!tokens.isEmpty() && tokens.getLast() instanceof Token.Dot) {
      tokens.add(new Token.IntegerLiteral(Long.parseLong(sb.toString()), loc));
      return;
    }
    if (peek() == '.' && Character.isDigit(peek(1))) {
      sb.append(advance()); // .
      while (pos < source.length() && Character.isDigit(peek())) {
        sb.append(advance());
      }
      // Check for e/E exponent
      if (peek() == 'e' || peek() == 'E') {
        sb.append(advance());
        if (peek() == '+' || peek() == '-') sb.append(advance());
        while (pos < source.length() && Character.isDigit(peek())) sb.append(advance());
      }
      tokens.add(new Token.FloatLiteral(Double.parseDouble(sb.toString()), loc));
    } else if (peek() == 'e' || peek() == 'E') {
      sb.append(advance());
      if (peek() == '+' || peek() == '-') sb.append(advance());
      while (pos < source.length() && Character.isDigit(peek())) sb.append(advance());
      tokens.add(new Token.FloatLiteral(Double.parseDouble(sb.toString()), loc));
    } else {
      tokens.add(new Token.IntegerLiteral(Long.parseLong(sb.toString()), loc));
    }
  }

  private void scanIdentifier(SourceLocation loc) {
    var sb = new StringBuilder();
    while (pos < source.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
      sb.append(advance());
    }
    var name = sb.toString();
    var keywordFactory = KEYWORDS.get(name);
    if (keywordFactory != null) {
      tokens.add(keywordFactory.apply(loc));
    } else {
      tokens.add(new Token.Identifier(name, loc));
    }
  }

  /**
   * Post-process tokens to apply trimNextTextLeft for whitespace control.
   * Called after tokenization to handle the trim-next-text-left flag set by
   * closing tags with - (e.g., -%}, -}}).
   */
  public List<Token> tokenizeAndTrim() {
    var raw = new ArrayList<>(tokenize());
    if (config.lstripBlocks()) {
      applyLstripBlocks(raw);
    }
    applyRightTrims(raw);
    if (!config.keepTrailingNewline()) {
      stripTrailingNewline(raw);
    }
    return Collections.unmodifiableList(raw);
  }

  /**
   * {@code lstrip_blocks}: strip the inline whitespace that precedes a block tag
   * when that tag begins a line. Only applies when the tag did not already use a
   * {@code -} left-trim (which removes all preceding whitespace including newlines).
   */
  private void applyLstripBlocks(List<Token> tokens) {
    for (int i = 1; i < tokens.size(); i++) {
      if (tokens.get(i) instanceof Token.StmtStart(var trimLeft, _) && !trimLeft) {
        if (tokens.get(i - 1) instanceof Token.Text(var val, var loc)) {
          int j = val.length();
          while (j > 0 && (val.charAt(j - 1) == ' ' || val.charAt(j - 1) == '\t')) j--;
          if (j == 0 || val.charAt(j - 1) == '\n') {
            tokens.set(i - 1, new Token.Text(val.substring(0, j), loc));
          }
        }
      }
    }
  }

  /**
   * Apply the right-side trims that affect the text following a tag:
   * a {@code -} closer strips all leading whitespace from the next text, while
   * {@code trim_blocks} removes a single newline after a block tag.
   */
  private void applyRightTrims(List<Token> tokens) {
    boolean stripLeadingWs = false;
    boolean removeOneNewline = false;
    for (int i = 0; i < tokens.size(); i++) {
      var tok = tokens.get(i);
      if ((stripLeadingWs || removeOneNewline) && tok instanceof Token.Text(var val, var loc)) {
        var s = stripLeadingWs ? val.stripLeading() : removeLeadingNewline(val);
        tokens.set(i, new Token.Text(s, loc));
      }
      if (!(tok instanceof Token.Text)) {
        stripLeadingWs = false;
        removeOneNewline = false;
      }
      if (tok instanceof Token.ExprEnd(var tr, _)) {
        stripLeadingWs = tr;
      } else if (tok instanceof Token.StmtEnd(var tr, _)) {
        stripLeadingWs = tr;
        removeOneNewline = !tr && config.trimBlocks();
      }
    }
  }

  private static String removeLeadingNewline(String s) {
    if (s.startsWith("\r\n")) return s.substring(2);
    if (s.startsWith("\n")) return s.substring(1);
    return s;
  }

  /** {@code keep_trailing_newline=false}: drop a single trailing newline at the end of the template. */
  private void stripTrailingNewline(List<Token> tokens) {
    for (int i = tokens.size() - 1; i >= 0; i--) {
      if (tokens.get(i) instanceof Token.Text(var val, var loc)) {
        String stripped = val.endsWith("\r\n")
          ? val.substring(0, val.length() - 2)
          : val.endsWith("\n")
            ? val.substring(0, val.length() - 1)
            : val;
        tokens.set(i, new Token.Text(stripped, loc));
        return;
      }
      if (!(tokens.get(i) instanceof Token.Eof)) {
        return; // last content isn't text — nothing to strip
      }
    }
  }
}
