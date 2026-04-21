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
import io.gravitee.jinja4j.ast.Node.Expr.*;
import io.gravitee.jinja4j.value.Value;
import java.util.*;

/**
 * Recursive-descent parser with Pratt expression parsing for Jinja2 templates.
 * Transforms a token stream into an AST.
 */
public final class Parser {

  private final List<Token> tokens;
  private final String templateName;
  private int pos;

  public Parser(List<Token> tokens, String templateName) {
    this.tokens = tokens;
    this.templateName = templateName;
    this.pos = 0;
  }

  public Node.Template parse() {
    var body = parseBody(Set.of());
    expect(Token.Eof.class, "end of template");
    return new Node.Template(body, new SourceLocation(templateName, 1, 1));
  }

  // ---- Token navigation ----

  private Token current() {
    return pos < tokens.size() ? tokens.get(pos) : tokens.getLast();
  }

  private Token peek() {
    return current();
  }

  private Token advance() {
    var tok = current();
    if (pos < tokens.size() - 1) pos++;
    return tok;
  }

  private boolean check(Class<? extends Token> type) {
    return type.isInstance(current());
  }

  private boolean checkIdentifier(String name) {
    return current() instanceof Token.Identifier(var n, _) && n.equals(name);
  }

  private <T extends Token> T expect(Class<T> type, String expected) {
    var tok = current();
    if (!type.isInstance(tok)) {
      throw new TemplateException("Expected %s but found %s".formatted(expected, describeToken(tok)), tok.location());
    }
    advance();
    return type.cast(tok);
  }

  private void expectStmtEnd() {
    var tok = current();
    if (!(tok instanceof Token.StmtEnd)) {
      throw new TemplateException("Expected %%} but found %s".formatted(describeToken(tok)), tok.location());
    }
    advance();
  }

  private String describeToken(Token tok) {
    return switch (tok) {
      case Token.Text(var v, _) -> "text '%s'".formatted(v.length() > 20 ? v.substring(0, 20) + "..." : v);
      case Token.Identifier(var n, _) -> "identifier '%s'".formatted(n);
      case Token.StringLiteral(var s, _) -> "string '%s'".formatted(s);
      case Token.IntegerLiteral(var i, _) -> "integer %d".formatted(i);
      case Token.FloatLiteral(var f, _) -> "float %f".formatted(f);
      case Token.BooleanLiteral(var b, _) -> b ? "true" : "false";
      case Token.NoneLiteral(_) -> "none";
      case Token.Eof(_) -> "end of file";
      case Token.StmtEnd(_, _) -> "%}";
      case Token.ExprEnd(_, _) -> "}}";
      case Token.StmtStart(_, _) -> "{%";
      case Token.ExprStart(_, _) -> "{{";
      default -> tok.getClass().getSimpleName();
    };
  }

  // ---- Body parsing ----

  private List<Node> parseBody(Set<String> stopTags) {
    var body = new ArrayList<Node>();
    while (!(current() instanceof Token.Eof)) {
      if (current() instanceof Token.Text text) {
        advance();
        if (!text.value().isEmpty()) {
          body.add(new Text(text.value(), text.location()));
        }
      } else if (current() instanceof Token.ExprStart) {
        body.add(parseOutput());
      } else if (current() instanceof Token.StmtStart) {
        // Peek at the next meaningful token after StmtStart
        int savedPos = pos;
        advance(); // skip StmtStart
        if (current() instanceof Token.Identifier(var name, _) && stopTags.contains(name)) {
          pos = savedPos; // restore position
          return body;
        }
        // Also check for 'else' / 'elif' keywords
        if (current() instanceof Token.Else(_) && stopTags.contains("else")) {
          pos = savedPos;
          return body;
        }
        if (current() instanceof Token.Elif(_) && stopTags.contains("elif")) {
          pos = savedPos;
          return body;
        }
        pos = savedPos;
        body.add(parseStatement());
      } else {
        // Shouldn't happen in a well-formed template
        advance();
      }
    }
    return body;
  }

  // ---- Output ({{ ... }}) ----

  private Node parseOutput() {
    var startTok = expect(Token.ExprStart.class, "{{");
    var expr = parseExpression();
    expect(Token.ExprEnd.class, "}}");
    return new Output(expr, startTok.location());
  }

  // ---- Statement dispatch ----

  private Node parseStatement() {
    var startTok = expect(Token.StmtStart.class, "{%");
    var loc = startTok.location();

    // Determine which statement
    var tok = current();
    Node result;

    if (tok instanceof Token.Identifier(var name, _)) {
      result = switch (name) {
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
    } else if (tok instanceof Token.If(_)) {
      result = parseIf(loc);
    } else {
      throw new TemplateException("Expected tag name but found %s".formatted(describeToken(tok)), tok.location());
    }

    return result;
  }

  // ---- If ----

  private Node parseIf(SourceLocation loc) {
    advance(); // skip 'if'
    var condition = parseExpression();
    expectStmtEnd();

    var branches = new ArrayList<ConditionBranch>();
    var body = parseBody(Set.of("elif", "else", "endif"));
    branches.add(new ConditionBranch(condition, body));

    List<Node> elseBranch = List.of();

    while (true) {
      expect(Token.StmtStart.class, "{%");
      var tok = current();
      if (tok instanceof Token.Elif(_)) {
        advance(); // skip 'elif'
        var elifCond = parseExpression();
        expectStmtEnd();
        var elifBody = parseBody(Set.of("elif", "else", "endif"));
        branches.add(new ConditionBranch(elifCond, elifBody));
      } else if (tok instanceof Token.Else(_)) {
        advance(); // skip 'else'
        expectStmtEnd();
        elseBranch = parseBody(Set.of("endif"));
        expect(Token.StmtStart.class, "{%");
        expectIdentifier("endif");
        expectStmtEnd();
        break;
      } else if (tok instanceof Token.Identifier(var n, _) && n.equals("endif")) {
        advance(); // skip 'endif'
        expectStmtEnd();
        break;
      } else {
        throw new TemplateException("Expected elif, else, or endif", tok.location());
      }
    }

    return new IfNode(branches, elseBranch, loc);
  }

  // ---- For ----

  private Node parseFor(SourceLocation loc) {
    advance(); // skip 'for'

    // Parse target(s): could be `item` or `key, value`
    String target = null;
    List<String> unpackTargets = null;

    var firstIdent = expectIdentifierName("loop variable");

    if (check(Token.Comma.class)) {
      // Tuple unpacking
      unpackTargets = new ArrayList<>();
      unpackTargets.add(firstIdent);
      while (check(Token.Comma.class)) {
        advance(); // skip comma
        unpackTargets.add(expectIdentifierName("loop variable"));
      }
    } else {
      target = firstIdent;
    }

    expectKeyword(Token.In.class, "in");
    var iterable = parseExpression();

    // Check for 'if' filter
    // TODO: for ... if ... filtering

    boolean recursive = false;
    if (checkIdentifier("recursive")) {
      advance();
      recursive = true;
    }

    expectStmtEnd();

    var body = parseBody(Set.of("else", "endfor"));

    List<Node> elseBranch = List.of();
    expect(Token.StmtStart.class, "{%");
    if (current() instanceof Token.Else(_)) {
      advance();
      expectStmtEnd();
      elseBranch = parseBody(Set.of("endfor"));
      expect(Token.StmtStart.class, "{%");
    }
    expectIdentifier("endfor");
    expectStmtEnd();

    return new ForNode(target, unpackTargets, iterable, body, elseBranch, recursive, loc);
  }

  // ---- Set ----

  private Node parseSet(SourceLocation loc) {
    advance(); // skip 'set'

    var firstName = expectIdentifierName("variable name");

    // Check for namespace attribute assignment: set ns.attr = value
    if (check(Token.Dot.class)) {
      advance(); // skip .
      var attrName = expectIdentifierName("attribute name");
      expect(Token.Assign.class, "=");
      var value = parseExpression();
      expectStmtEnd();
      return new SetAttrNode(firstName, attrName, value, loc);
    }

    expect(Token.Assign.class, "=");
    var value = parseExpression();
    expectStmtEnd();
    return new SetNode(firstName, value, loc);
  }

  // ---- Block ----

  private Node parseBlock(SourceLocation loc) {
    advance(); // skip 'block'
    var name = expectIdentifierName("block name");
    expectStmtEnd();
    var body = parseBody(Set.of("endblock"));
    expect(Token.StmtStart.class, "{%");
    expectIdentifier("endblock");
    // Optional block name after endblock
    if (current() instanceof Token.Identifier(var n, _)) {
      advance();
    }
    expectStmtEnd();
    return new BlockNode(name, body, loc);
  }

  // ---- Extends ----

  private Node parseExtends(SourceLocation loc) {
    advance(); // skip 'extends'
    var templateExpr = parseExpression();
    expectStmtEnd();
    return new ExtendsNode(templateExpr, loc);
  }

  // ---- Include ----

  private Node parseInclude(SourceLocation loc) {
    advance(); // skip 'include'
    var templateExpr = parseExpression();
    boolean ignoreMissing = false;
    if (checkIdentifier("ignore") && pos + 1 < tokens.size()) {
      // Check for "ignore missing"
      int savedPos = pos;
      advance();
      if (checkIdentifier("missing")) {
        advance();
        ignoreMissing = true;
      } else {
        pos = savedPos;
      }
    }
    expectStmtEnd();
    return new IncludeNode(templateExpr, ignoreMissing, loc);
  }

  // ---- Macro ----

  private Node parseMacro(SourceLocation loc) {
    advance(); // skip 'macro'
    var name = expectIdentifierName("macro name");
    expect(Token.LParen.class, "(");

    var args = new ArrayList<MacroArg>();
    while (!check(Token.RParen.class)) {
      if (!args.isEmpty()) {
        expect(Token.Comma.class, ",");
      }
      var argName = expectIdentifierName("argument name");
      Expr defaultValue = null;
      if (check(Token.Assign.class)) {
        advance();
        defaultValue = parseExpression();
      }
      args.add(new MacroArg(argName, defaultValue));
    }
    expect(Token.RParen.class, ")");
    expectStmtEnd();

    var body = parseBody(Set.of("endmacro"));
    expect(Token.StmtStart.class, "{%");
    expectIdentifier("endmacro");
    expectStmtEnd();

    return new MacroNode(name, args, body, loc);
  }

  // ---- Call block ----

  private Node parseCallBlock(SourceLocation loc) {
    advance(); // skip 'call'
    // For now, just parse as output
    var expr = parseExpression();
    expectStmtEnd();
    var body = parseBody(Set.of("endcall"));
    expect(Token.StmtStart.class, "{%");
    expectIdentifier("endcall");
    expectStmtEnd();
    return new Output(expr, loc);
  }

  // ---- With ----

  private Node parseWith(SourceLocation loc) {
    advance(); // skip 'with'

    var assignments = new ArrayList<SetNode>();
    // Parse assignments until %}
    while (!(current() instanceof Token.StmtEnd)) {
      var varName = expectIdentifierName("variable name");
      expect(Token.Assign.class, "=");
      var value = parseExpression();
      assignments.add(new SetNode(varName, value, loc));
      if (check(Token.Comma.class)) advance();
    }
    expectStmtEnd();

    var body = parseBody(Set.of("endwith"));
    expect(Token.StmtStart.class, "{%");
    expectIdentifier("endwith");
    expectStmtEnd();

    return new WithNode(assignments, body, loc);
  }

  // ---- Filter block ----

  private Node parseFilterBlock(SourceLocation loc) {
    advance(); // skip 'filter'
    var filterName = expectIdentifierName("filter name");
    var args = new ArrayList<Expr>();
    if (check(Token.LParen.class)) {
      advance();
      while (!check(Token.RParen.class)) {
        if (!args.isEmpty()) expect(Token.Comma.class, ",");
        args.add(parseExpression());
      }
      expect(Token.RParen.class, ")");
    }
    expectStmtEnd();

    var body = parseBody(Set.of("endfilter"));
    expect(Token.StmtStart.class, "{%");
    expectIdentifier("endfilter");
    expectStmtEnd();

    return new FilterBlockNode(filterName, args, body, loc);
  }

  // ---- Raw ----

  private Node parseRaw(SourceLocation loc) {
    advance(); // skip 'raw'
    expectStmtEnd();

    // Collect raw text until {% endraw %}
    var sb = new StringBuilder();
    while (pos < tokens.size()) {
      var tok = current();
      if (tok instanceof Token.StmtStart) {
        int savedPos = pos;
        advance();
        if (current() instanceof Token.Identifier(var n, _) && n.equals("endraw")) {
          advance();
          expectStmtEnd();
          return new RawNode(sb.toString(), loc);
        }
        pos = savedPos;
        // Not endraw, include as text
        sb.append("{%");
        advance();
      } else if (tok instanceof Token.Text(var v, _)) {
        sb.append(v);
        advance();
      } else if (tok instanceof Token.ExprStart(_, _)) {
        sb.append("{{");
        advance();
      } else if (tok instanceof Token.ExprEnd(_, _)) {
        sb.append("}}");
        advance();
      } else {
        sb.append(describeToken(tok));
        advance();
      }
    }
    throw new TemplateException("Unclosed raw block", loc);
  }

  // ---- Generation (Generation tag) ----

  private Node parseGeneration(SourceLocation loc) {
    advance(); // skip 'generation'
    expectStmtEnd();
    return new GenerationNode(loc);
  }

  // ---- Expression parser (Pratt-style precedence climbing) ----

  public Expr parseExpression() {
    return parseConditional();
  }

  private Expr parseConditional() {
    var expr = parseOr();

    // Check for inline if: expr if condition else other
    if (current() instanceof Token.If(_)) {
      advance(); // skip 'if'
      var condition = parseOr();
      Expr falseExpr;
      if (current() instanceof Token.Else(_)) {
        advance(); // skip 'else'
        falseExpr = parseConditional(); // right-associative
      } else {
        falseExpr = new Literal(Value.UNDEFINED, expr.location());
      }
      return new Ternary(expr, condition, falseExpr, expr.location());
    }

    return expr;
  }

  private Expr parseOr() {
    var left = parseAnd();
    while (current() instanceof Token.Or(_)) {
      advance();
      var right = parseAnd();
      left = new BinOp(left, Node.BinOperator.OR, right, left.location());
    }
    return left;
  }

  private Expr parseAnd() {
    var left = parseNot();
    while (current() instanceof Token.And(_)) {
      advance();
      var right = parseNot();
      left = new BinOp(left, Node.BinOperator.AND, right, left.location());
    }
    return left;
  }

  private Expr parseNot() {
    if (current() instanceof Token.Not(var loc)) {
      advance();
      var operand = parseNot();
      return new UnaryOp(Node.UnaryOperator.NOT, operand, loc);
    }
    return parseCompare();
  }

  private Expr parseCompare() {
    var left = parseConcatOrAdd();
    while (true) {
      var tok = current();
      if (tok instanceof Token.Eq(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.EQ, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Ne(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.NE, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Lt(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.LT, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Gt(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.GT, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Le(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.LE, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Ge(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.GE, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.In(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.IN, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Not(var loc)) {
        advance();
        expectKeyword(Token.In.class, "in");
        left = new BinOp(left, Node.BinOperator.NOT_IN, parseConcatOrAdd(), left.location());
      } else if (tok instanceof Token.Is(var loc)) {
        advance();
        boolean negated = false;
        if (current() instanceof Token.Not(_)) {
          advance();
          negated = true;
        }
        var testName = parseTestName();
        var args = new ArrayList<Expr>();
        if (check(Token.LParen.class)) {
          advance();
          while (!check(Token.RParen.class)) {
            if (!args.isEmpty()) expect(Token.Comma.class, ",");
            args.add(parseExpression());
          }
          expect(Token.RParen.class, ")");
        }
        left = new Test(left, testName, args, negated, left.location());
      } else break;
    }
    return left;
  }

  private String parseTestName() {
    var tok = current();
    if (tok instanceof Token.Identifier(var name, _)) {
      advance();
      return name;
    }
    // Some tests use keyword names (like 'none', 'true', etc.)
    if (tok instanceof Token.NoneLiteral(_)) {
      advance();
      return "none";
    }
    if (tok instanceof Token.BooleanLiteral(var v, _)) {
      advance();
      return v ? "true" : "false";
    }
    throw new TemplateException("Expected test name but found %s".formatted(describeToken(tok)), tok.location());
  }

  private Expr parseConcatOrAdd() {
    var left = parseMulDiv();
    while (true) {
      var tok = current();
      if (tok instanceof Token.Plus(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.ADD, parseMulDiv(), left.location());
      } else if (tok instanceof Token.Minus(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.SUB, parseMulDiv(), left.location());
      } else if (tok instanceof Token.Tilde(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.CONCAT, parseMulDiv(), left.location());
      } else break;
    }
    return left;
  }

  private Expr parseMulDiv() {
    var left = parseUnary();
    while (true) {
      var tok = current();
      if (tok instanceof Token.Star(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.MUL, parseUnary(), left.location());
      } else if (tok instanceof Token.Slash(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.DIV, parseUnary(), left.location());
      } else if (tok instanceof Token.FloorDiv(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.FLOOR_DIV, parseUnary(), left.location());
      } else if (tok instanceof Token.Percent(_)) {
        advance();
        left = new BinOp(left, Node.BinOperator.MOD, parseUnary(), left.location());
      } else break;
    }
    return left;
  }

  private Expr parseUnary() {
    if (current() instanceof Token.Minus(var loc)) {
      advance();
      return new UnaryOp(Node.UnaryOperator.NEG, parsePower(), loc);
    }
    if (current() instanceof Token.Plus(_)) {
      advance();
      return parsePower();
    }
    return parsePower();
  }

  private Expr parsePower() {
    var base = parsePostfix();
    if (current() instanceof Token.Power(_)) {
      advance();
      var exp = parseUnary(); // right-associative
      return new BinOp(base, Node.BinOperator.POW, exp, base.location());
    }
    return base;
  }

  private Expr parsePostfix() {
    var expr = parsePrimary();

    while (true) {
      if (current() instanceof Token.Dot(_)) {
        advance();
        var attrName = expectIdentifierName("attribute name");
        expr = new GetAttr(expr, attrName, expr.location());
      } else if (current() instanceof Token.LBracket(_)) {
        advance();
        // Check for slice syntax
        if (current() instanceof Token.Colon(_)) {
          expr = parseSlice(expr, null);
        } else {
          var key = parseExpression();
          if (current() instanceof Token.Colon(_)) {
            expr = parseSlice(expr, key);
          } else {
            expect(Token.RBracket.class, "]");
            expr = new GetItem(expr, key, expr.location());
          }
        }
      } else if (current() instanceof Token.LParen(_)) {
        advance();
        var args = new ArrayList<Expr>();
        var kwargs = new LinkedHashMap<String, Expr>();
        parseArgList(args, kwargs);
        expect(Token.RParen.class, ")");
        expr = new Call(expr, args, kwargs, expr.location());
      } else if (current() instanceof Token.Pipe(_)) {
        advance();
        var filterName = expectIdentifierName("filter name");
        var args = new ArrayList<Expr>();
        var kwargs = new LinkedHashMap<String, Expr>();
        if (check(Token.LParen.class)) {
          advance();
          parseArgList(args, kwargs);
          expect(Token.RParen.class, ")");
        }
        expr = new Filter(expr, filterName, args, kwargs, expr.location());
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr parseSlice(Expr object, Expr start, SourceLocation... loc) {
    // At this point we've seen '[' and optionally start, and we're on ':'
    advance(); // skip ':'
    Expr stop = null;
    Expr step = null;
    if (!(current() instanceof Token.RBracket) && !(current() instanceof Token.Colon)) {
      stop = parseExpression();
    }
    if (current() instanceof Token.Colon(_)) {
      advance();
      if (!(current() instanceof Token.RBracket)) {
        step = parseExpression();
      }
    }
    expect(Token.RBracket.class, "]");
    return new Slice(object, start, stop, step, object.location());
  }

  private void parseArgList(List<Expr> args, Map<String, Expr> kwargs) {
    while (!check(Token.RParen.class)) {
      if (!args.isEmpty() || !kwargs.isEmpty()) {
        expect(Token.Comma.class, ",");
        if (check(Token.RParen.class)) break; // trailing comma
      }
      // Check if this is a keyword argument: name=value
      if (
        current() instanceof Token.Identifier(var name, _) &&
        pos + 1 < tokens.size() &&
        tokens.get(pos + 1) instanceof Token.Assign(_)
      ) {
        advance(); // skip name
        advance(); // skip =
        kwargs.put(name, parseExpression());
      } else {
        args.add(parseExpression());
      }
    }
  }

  private Expr parsePrimary() {
    var tok = current();

    // Literals
    if (tok instanceof Token.StringLiteral(var s, var loc)) {
      advance();
      // Check for string concatenation with adjacent string literals (not Jinja feature, but useful)
      return new Literal(Value.of(s), loc);
    }
    if (tok instanceof Token.IntegerLiteral(var i, var loc)) {
      advance();
      return new Literal(Value.of(i), loc);
    }
    if (tok instanceof Token.FloatLiteral(var f, var loc)) {
      advance();
      return new Literal(Value.of(f), loc);
    }
    if (tok instanceof Token.BooleanLiteral(var b, var loc)) {
      advance();
      return new Literal(Value.of(b), loc);
    }
    if (tok instanceof Token.NoneLiteral(var loc)) {
      advance();
      return new Literal(Value.NULL, loc);
    }

    // Names
    if (tok instanceof Token.Identifier(var name, var loc)) {
      advance();
      return new Name(name, loc);
    }

    // Parenthesized expression or tuple
    if (tok instanceof Token.LParen(var loc)) {
      advance();
      if (check(Token.RParen.class)) {
        advance();
        return new TupleLiteral(List.of(), loc);
      }
      var first = parseExpression();
      if (check(Token.Comma.class)) {
        // Tuple
        var items = new ArrayList<Expr>();
        items.add(first);
        while (check(Token.Comma.class)) {
          advance();
          if (check(Token.RParen.class)) break;
          items.add(parseExpression());
        }
        expect(Token.RParen.class, ")");
        return new TupleLiteral(items, loc);
      }
      expect(Token.RParen.class, ")");
      return first;
    }

    // List literal
    if (tok instanceof Token.LBracket(var loc)) {
      advance();
      var items = new ArrayList<Expr>();
      while (!check(Token.RBracket.class)) {
        if (!items.isEmpty()) expect(Token.Comma.class, ",");
        if (check(Token.RBracket.class)) break;
        items.add(parseExpression());
      }
      expect(Token.RBracket.class, "]");
      return new ListLiteral(items, loc);
    }

    // Dict literal
    if (tok instanceof Token.LBrace(var loc)) {
      advance();
      var entries = new ArrayList<Map.Entry<Expr, Expr>>();
      while (!check(Token.RBrace.class)) {
        if (!entries.isEmpty()) expect(Token.Comma.class, ",");
        if (check(Token.RBrace.class)) break;
        var key = parseExpression();
        expect(Token.Colon.class, ":");
        var value = parseExpression();
        entries.add(Map.entry(key, value));
      }
      expect(Token.RBrace.class, "}");
      return new DictLiteral(entries, loc);
    }

    throw new TemplateException("Unexpected token %s in expression".formatted(describeToken(tok)), tok.location());
  }

  // ---- Helpers ----

  private String expectIdentifierName(String what) {
    var tok = current();
    if (tok instanceof Token.Identifier(var name, _)) {
      advance();
      return name;
    }
    throw new TemplateException("Expected %s but found %s".formatted(what, describeToken(tok)), tok.location());
  }

  private void expectIdentifier(String expected) {
    var tok = current();
    if (tok instanceof Token.Identifier(var name, _) && name.equals(expected)) {
      advance();
      return;
    }
    throw new TemplateException("Expected '%s' but found %s".formatted(expected, describeToken(tok)), tok.location());
  }

  private <T extends Token> void expectKeyword(Class<T> type, String keyword) {
    var tok = current();
    if (type.isInstance(tok)) {
      advance();
      return;
    }
    throw new TemplateException("Expected '%s' but found %s".formatted(keyword, describeToken(tok)), tok.location());
  }
}
