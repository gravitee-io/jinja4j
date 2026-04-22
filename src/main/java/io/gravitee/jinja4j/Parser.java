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

import io.gravitee.jinja4j.ast.Node;
import io.gravitee.jinja4j.ast.Node.*;
import io.gravitee.jinja4j.ast.Node.Expr;
import io.gravitee.jinja4j.parser.ExpressionParser;
import io.gravitee.jinja4j.parser.TokenCursor;
import io.gravitee.jinja4j.parser.TokenDescriber;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for Jinja2 statement-level constructs.
 *
 * <p>Dispatches on the identifier following {@code {%} and delegates
 * expression parsing to the shared {@link ExpressionParser} via a
 * shared {@link TokenCursor}. Each {@code parseXxx} method starts with
 * the cursor positioned on the tag name (just past {@code {%}) and
 * returns with the cursor past the closing {@code %}}.</p>
 */
public final class Parser {

  private final TokenCursor cursor;
  private final ExpressionParser expressions;

  public Parser(List<Token> tokens, String templateName) {
    this.cursor = new TokenCursor(tokens, templateName);
    this.expressions = new ExpressionParser(cursor);
  }

  public Node.Template parse() {
    var body = parseBody(Set.of());
    cursor.expect(Token.Eof.class, "end of template");
    return new Node.Template(body, new SourceLocation(cursor.templateName(), 1, 1));
  }

  // ---- Body parsing ----

  /**
   * Parse a sequence of nodes until EOF or until a statement tag whose
   * name is in {@code stopTags}. Stop tokens are NOT consumed: the caller
   * remains on the {@code {%} that opens them.
   */
  private List<Node> parseBody(Set<String> stopTags) {
    var body = new ArrayList<Node>();
    while (!cursor.isAtEnd()) {
      var tok = cursor.current();
      switch (tok) {
        case Token.Text(String value, SourceLocation location) -> {
          cursor.advance();
          if (!value.isEmpty()) body.add(new Text(value, location));
        }
        case Token.ExprStart ignored -> body.add(parseOutput());
        case Token.StmtStart ignored -> {
          if (atStopTag(stopTags)) return body;
          body.add(parseStatement());
        }
        default -> cursor.advance(); // shouldn't happen in a well-formed template
      }
    }
    return body;
  }

  /**
   * Look one token past {@code {%} without consuming the stream. Returns
   * true when that token is an identifier (or else/elif keyword) that
   * matches {@code stopTags}.
   */
  private boolean atStopTag(Set<String> stopTags) {
    var lookahead = cursor.peekAhead(1);
    return switch (lookahead) {
      case Token.Identifier(String name, var ignored) -> stopTags.contains(name);
      case Token.Else ignored -> stopTags.contains("else");
      case Token.Elif ignored -> stopTags.contains("elif");
      default -> false;
    };
  }

  // ---- Output ({{ ... }}) ----

  private Node parseOutput() {
    var startTok = cursor.expect(Token.ExprStart.class, "{{");
    var expr = expressions.parseExpression();
    cursor.expect(Token.ExprEnd.class, "}}");
    return new Output(expr, startTok.location());
  }

  // ---- Statement dispatch ----

  private Node parseStatement() {
    var startTok = cursor.expect(Token.StmtStart.class, "{%");
    var loc = startTok.location();
    var tok = cursor.current();

    if (tok instanceof Token.If(var ignored)) return parseIf(loc);
    if (tok instanceof Token.Identifier(String name, var ignored2)) {
      return switch (name) {
        case "for" -> parseFor(loc);
        case "set" -> parseSet(loc);
        case "block" -> parseBlock(loc);
        case "extends" -> parseExtends(loc);
        case "include" -> parseInclude(loc);
        case "macro" -> parseMacro(loc);
        case "call" -> parseCallBlock(loc);
        case "with" -> parseWith(loc);
        case "filter" -> parseFilterBlock(loc);
        case "raw" -> parseRaw(loc);
        case "generation" -> parseGeneration(loc);
        default -> throw new TemplateException("Unknown tag '%s'".formatted(name), tok.location());
      };
    }
    throw new TemplateException("Expected tag name but found %s".formatted(TokenDescriber.describe(tok)), tok.location());
  }

  // ---- If ----

  private Node parseIf(SourceLocation loc) {
    cursor.advance(); // skip 'if'
    var condition = expressions.parseExpression();
    cursor.expectStmtEnd();

    var branches = new ArrayList<ConditionBranch>();
    branches.add(new ConditionBranch(condition, parseBody(Set.of("elif", "else", "endif"))));

    List<Node> elseBranch = List.of();
    while (true) {
      cursor.expect(Token.StmtStart.class, "{%");
      var tok = cursor.current();
      switch (tok) {
        case Token.Elif ignored -> {
          cursor.advance();
          var elifCond = expressions.parseExpression();
          cursor.expectStmtEnd();
          branches.add(new ConditionBranch(elifCond, parseBody(Set.of("elif", "else", "endif"))));
        }
        case Token.Else ignored -> {
          cursor.advance();
          cursor.expectStmtEnd();
          elseBranch = parseBody(Set.of("endif"));
          cursor.expect(Token.StmtStart.class, "{%");
          cursor.expectIdentifier("endif");
          cursor.expectStmtEnd();
          return new IfNode(branches, elseBranch, loc);
        }
        case Token.Identifier(String n, var ignored) when n.equals("endif") -> {
          cursor.advance();
          cursor.expectStmtEnd();
          return new IfNode(branches, elseBranch, loc);
        }
        default -> throw new TemplateException("Expected elif, else, or endif", tok.location());
      }
    }
  }

  // ---- For ----

  private Node parseFor(SourceLocation loc) {
    cursor.advance(); // skip 'for'

    // Parse target(s): could be 'item' or 'key, value'
    String target = null;
    List<String> unpackTargets = null;
    var firstIdent = cursor.expectIdentifierName("loop variable");
    if (cursor.check(Token.Comma.class)) {
      unpackTargets = new ArrayList<>();
      unpackTargets.add(firstIdent);
      while (cursor.check(Token.Comma.class)) {
        cursor.advance();
        unpackTargets.add(cursor.expectIdentifierName("loop variable"));
      }
    } else {
      target = firstIdent;
    }

    cursor.expectKeyword(Token.In.class, "in");
    var iterable = expressions.parseExpression();

    boolean recursive = false;
    if (cursor.checkIdentifier("recursive")) {
      cursor.advance();
      recursive = true;
    }

    cursor.expectStmtEnd();
    var body = parseBody(Set.of("else", "endfor"));

    List<Node> elseBranch = List.of();
    cursor.expect(Token.StmtStart.class, "{%");
    if (cursor.current() instanceof Token.Else(var ignored)) {
      cursor.advance();
      cursor.expectStmtEnd();
      elseBranch = parseBody(Set.of("endfor"));
      cursor.expect(Token.StmtStart.class, "{%");
    }
    cursor.expectIdentifier("endfor");
    cursor.expectStmtEnd();

    return new ForNode(target, unpackTargets, iterable, body, elseBranch, recursive, loc);
  }

  // ---- Set ----

  private Node parseSet(SourceLocation loc) {
    cursor.advance(); // skip 'set'
    var firstName = cursor.expectIdentifierName("variable name");

    // Namespace attribute assignment: set ns.attr = value
    if (cursor.check(Token.Dot.class)) {
      cursor.advance();
      var attrName = cursor.expectIdentifierName("attribute name");
      cursor.expect(Token.Assign.class, "=");
      var value = expressions.parseExpression();
      cursor.expectStmtEnd();
      return new SetAttrNode(firstName, attrName, value, loc);
    }

    cursor.expect(Token.Assign.class, "=");
    var value = expressions.parseExpression();
    cursor.expectStmtEnd();
    return new SetNode(firstName, value, loc);
  }

  // ---- Block ----

  private Node parseBlock(SourceLocation loc) {
    cursor.advance(); // skip 'block'
    var name = cursor.expectIdentifierName("block name");
    cursor.expectStmtEnd();
    var body = parseBody(Set.of("endblock"));
    cursor.expect(Token.StmtStart.class, "{%");
    cursor.expectIdentifier("endblock");
    // Optional block name after endblock
    if (cursor.current() instanceof Token.Identifier(var ignored, var ignored2)) {
      cursor.advance();
    }
    cursor.expectStmtEnd();
    return new BlockNode(name, body, loc);
  }

  // ---- Extends ----

  private Node parseExtends(SourceLocation loc) {
    cursor.advance(); // skip 'extends'
    var templateExpr = expressions.parseExpression();
    cursor.expectStmtEnd();
    return new ExtendsNode(templateExpr, loc);
  }

  // ---- Include ----

  private Node parseInclude(SourceLocation loc) {
    cursor.advance(); // skip 'include'
    var templateExpr = expressions.parseExpression();
    boolean ignoreMissing = false;
    if (cursor.checkIdentifier("ignore")) {
      int savedPos = cursor.mark();
      cursor.advance();
      if (cursor.checkIdentifier("missing")) {
        cursor.advance();
        ignoreMissing = true;
      } else {
        cursor.reset(savedPos);
      }
    }
    cursor.expectStmtEnd();
    return new IncludeNode(templateExpr, ignoreMissing, loc);
  }

  // ---- Macro ----

  private Node parseMacro(SourceLocation loc) {
    cursor.advance(); // skip 'macro'
    var name = cursor.expectIdentifierName("macro name");
    cursor.expect(Token.LParen.class, "(");

    var args = new ArrayList<MacroArg>();
    while (!cursor.check(Token.RParen.class)) {
      if (!args.isEmpty()) cursor.expect(Token.Comma.class, ",");
      var argName = cursor.expectIdentifierName("argument name");
      Expr defaultValue = null;
      if (cursor.check(Token.Assign.class)) {
        cursor.advance();
        defaultValue = expressions.parseExpression();
      }
      args.add(new MacroArg(argName, defaultValue));
    }
    cursor.expect(Token.RParen.class, ")");
    cursor.expectStmtEnd();

    var body = parseBody(Set.of("endmacro"));
    cursor.expect(Token.StmtStart.class, "{%");
    cursor.expectIdentifier("endmacro");
    cursor.expectStmtEnd();

    return new MacroNode(name, args, body, loc);
  }

  // ---- Call block ----

  private Node parseCallBlock(SourceLocation loc) {
    cursor.advance(); // skip 'call'
    var call = expressions.parseExpression();
    cursor.expectStmtEnd();
    var body = parseBody(Set.of("endcall"));
    cursor.expect(Token.StmtStart.class, "{%");
    cursor.expectIdentifier("endcall");
    cursor.expectStmtEnd();
    return new CallBlockNode(call, body, loc);
  }

  // ---- With ----

  private Node parseWith(SourceLocation loc) {
    cursor.advance(); // skip 'with'
    var assignments = new ArrayList<SetNode>();
    while (!(cursor.current() instanceof Token.StmtEnd)) {
      var varName = cursor.expectIdentifierName("variable name");
      cursor.expect(Token.Assign.class, "=");
      var value = expressions.parseExpression();
      assignments.add(new SetNode(varName, value, loc));
      if (cursor.check(Token.Comma.class)) cursor.advance();
    }
    cursor.expectStmtEnd();

    var body = parseBody(Set.of("endwith"));
    cursor.expect(Token.StmtStart.class, "{%");
    cursor.expectIdentifier("endwith");
    cursor.expectStmtEnd();

    return new WithNode(assignments, body, loc);
  }

  // ---- Filter block ----

  private Node parseFilterBlock(SourceLocation loc) {
    cursor.advance(); // skip 'filter'
    var filterName = cursor.expectIdentifierName("filter name");
    var args = new ArrayList<Expr>();
    if (cursor.check(Token.LParen.class)) {
      cursor.advance();
      while (!cursor.check(Token.RParen.class)) {
        if (!args.isEmpty()) cursor.expect(Token.Comma.class, ",");
        args.add(expressions.parseExpression());
      }
      cursor.expect(Token.RParen.class, ")");
    }
    cursor.expectStmtEnd();

    var body = parseBody(Set.of("endfilter"));
    cursor.expect(Token.StmtStart.class, "{%");
    cursor.expectIdentifier("endfilter");
    cursor.expectStmtEnd();

    return new FilterBlockNode(filterName, args, body, loc);
  }

  // ---- Raw ----

  private Node parseRaw(SourceLocation loc) {
    cursor.advance(); // skip 'raw'
    cursor.expectStmtEnd();

    var sb = new StringBuilder();
    while (!cursor.isAtEnd()) {
      var tok = cursor.current();
      switch (tok) {
        case Token.StmtStart ignored -> {
          int savedPos = cursor.mark();
          cursor.advance();
          if (cursor.current() instanceof Token.Identifier(String n, var ignored2) && n.equals("endraw")) {
            cursor.advance();
            cursor.expectStmtEnd();
            return new RawNode(sb.toString(), loc);
          }
          cursor.reset(savedPos);
          sb.append("{%");
          cursor.advance();
        }
        case Token.Text(String v, var ignored) -> {
          sb.append(v);
          cursor.advance();
        }
        case Token.ExprStart ignored -> {
          sb.append("{{");
          cursor.advance();
        }
        case Token.ExprEnd ignored -> {
          sb.append("}}");
          cursor.advance();
        }
        case null, default -> {
          sb.append(TokenDescriber.describe(tok));
          cursor.advance();
        }
      }
    }
    throw new TemplateException("Unclosed raw block", loc);
  }

  // ---- Generation ----

  private Node parseGeneration(SourceLocation loc) {
    cursor.advance(); // skip 'generation'
    cursor.expectStmtEnd();
    return new GenerationNode(loc);
  }
}
