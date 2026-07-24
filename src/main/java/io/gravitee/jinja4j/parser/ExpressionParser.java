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

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.Token;
import io.gravitee.jinja4j.ast.Node;
import io.gravitee.jinja4j.ast.Node.Expr;
import io.gravitee.jinja4j.ast.Node.Expr.*;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pratt-style precedence-climbing parser for Jinja2 expressions.
 *
 * <p>Public entry point is {@link #parseExpression()}. All other methods
 * are private precedence levels, climbing from the loosest operator
 * ({@code if-else} ternary) down to atomic primaries (literals,
 * identifiers, parenthesised groups, list/dict literals).</p>
 *
 * <p>Operates over a shared {@link TokenCursor} so that a caller
 * (typically a statement parser) can seamlessly interleave expression
 * parsing with its own token consumption.</p>
 */
public final class ExpressionParser {

  private final TokenCursor cursor;

  public ExpressionParser(TokenCursor cursor) {
    this.cursor = cursor;
  }

  public Expr parseExpression() {
    return parseConditional();
  }

  /**
   * Parses an expression without consuming an inline ternary {@code if}.
   * Used where a bare {@code if} has statement-level meaning, e.g. the
   * iterable and filter condition of {@code {% for x in seq if cond %}}.
   */
  public Expr parseExpressionNoCondition() {
    return parseOr();
  }

  // ---- Precedence levels (loosest to tightest) ----

  private Expr parseConditional() {
    var expr = parseOr();
    // Inline ternary:  a if cond else b
    if (cursor.current() instanceof Token.If(var ignored)) {
      cursor.advance();
      var condition = parseOr();
      Expr falseExpr;
      if (cursor.current() instanceof Token.Else(var ignored2)) {
        cursor.advance();
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
    while (cursor.current() instanceof Token.Or(var ignored)) {
      cursor.advance();
      left = new BinOp(left, Node.BinOperator.OR, parseAnd(), left.location());
    }
    return left;
  }

  private Expr parseAnd() {
    var left = parseNot();
    while (cursor.current() instanceof Token.And(var ignored)) {
      cursor.advance();
      left = new BinOp(left, Node.BinOperator.AND, parseNot(), left.location());
    }
    return left;
  }

  private Expr parseNot() {
    if (cursor.current() instanceof Token.Not(var loc)) {
      cursor.advance();
      return new UnaryOp(Node.UnaryOperator.NOT, parseNot(), loc);
    }
    return parseCompare();
  }

  private Expr parseCompare() {
    var left = parseConcatOrAdd();

    // Collect a (possibly chained) comparison: a < b < c. operands has one
    // more entry than operators; a single comparison degrades to a plain BinOp
    // to preserve existing evaluation semantics.
    List<Expr> operands = null;
    List<Node.BinOperator> operators = null;

    while (true) {
      var tok = cursor.current();
      Node.BinOperator op = matchComparison(tok);
      if (op != null) {
        cursor.advance();
      } else if (tok instanceof Token.Not(var ignored)) {
        // 'not in'
        cursor.advance();
        cursor.expectKeyword(Token.In.class, "in");
        op = Node.BinOperator.NOT_IN;
      } else if (tok instanceof Token.Is(var ignored)) {
        if (operators != null) return new Compare(operands, operators, left.location());
        left = parseIsTest(left);
        continue;
      } else if (operators != null) {
        return new Compare(operands, operators, left.location());
      } else {
        return left;
      }

      var right = parseConcatOrAdd();
      if (operators == null) {
        operators = new ArrayList<>();
        operands = new ArrayList<>();
        operands.add(left);
      }
      operators.add(op);
      operands.add(right);
      if (operators.size() == 1) {
        // Keep degrading to BinOp until a second comparison appears.
        left = new BinOp(operands.get(0), op, right, left.location());
      }
    }
  }

  private static Node.BinOperator matchComparison(Token tok) {
    return switch (tok) {
      case Token.Eq(var ignored) -> Node.BinOperator.EQ;
      case Token.Ne(var ignored) -> Node.BinOperator.NE;
      case Token.Lt(var ignored) -> Node.BinOperator.LT;
      case Token.Gt(var ignored) -> Node.BinOperator.GT;
      case Token.Le(var ignored) -> Node.BinOperator.LE;
      case Token.Ge(var ignored) -> Node.BinOperator.GE;
      case Token.In(var ignored) -> Node.BinOperator.IN;
      default -> null;
    };
  }

  private Expr parseIsTest(Expr left) {
    cursor.advance(); // skip 'is'
    boolean negated = false;
    if (cursor.current() instanceof Token.Not(var ignored)) {
      cursor.advance();
      negated = true;
    }
    var testName = parseTestName();
    var args = new ArrayList<Expr>();
    if (cursor.check(Token.LParen.class)) {
      cursor.advance();
      while (!cursor.check(Token.RParen.class)) {
        if (!args.isEmpty()) cursor.expect(Token.Comma.class, ",");
        args.add(parseExpression());
      }
      cursor.expect(Token.RParen.class, ")");
    }
    return new Test(left, testName, args, negated, left.location());
  }

  private String parseTestName() {
    var tok = cursor.current();
    return switch (tok) {
      case Token.Identifier(String name, var ignored) -> {
        cursor.advance();
        yield name;
      }
      // Some tests use keyword names (like 'none', 'true', etc.)
      case Token.NoneLiteral(var ignored) -> {
        cursor.advance();
        yield "none";
      }
      case Token.BooleanLiteral(boolean v, var ignored) -> {
        cursor.advance();
        yield v ? "true" : "false";
      }
      default -> throw new TemplateException(
        "Expected test name but found %s".formatted(TokenDescriber.describe(tok)),
        tok.location()
      );
    };
  }

  private Expr parseConcatOrAdd() {
    var left = parseMulDiv();
    while (true) {
      var op = matchAddOp(cursor.current());
      if (op == null) return left;
      cursor.advance();
      left = new BinOp(left, op, parseMulDiv(), left.location());
    }
  }

  private static Node.BinOperator matchAddOp(Token tok) {
    return switch (tok) {
      case Token.Plus(var ignored) -> Node.BinOperator.ADD;
      case Token.Minus(var ignored) -> Node.BinOperator.SUB;
      case Token.Tilde(var ignored) -> Node.BinOperator.CONCAT;
      default -> null;
    };
  }

  private Expr parseMulDiv() {
    var left = parseUnary();
    while (true) {
      var op = matchMulOp(cursor.current());
      if (op == null) return left;
      cursor.advance();
      left = new BinOp(left, op, parseUnary(), left.location());
    }
  }

  private static Node.BinOperator matchMulOp(Token tok) {
    return switch (tok) {
      case Token.Star(var ignored) -> Node.BinOperator.MUL;
      case Token.Slash(var ignored) -> Node.BinOperator.DIV;
      case Token.FloorDiv(var ignored) -> Node.BinOperator.FLOOR_DIV;
      case Token.Percent(var ignored) -> Node.BinOperator.MOD;
      default -> null;
    };
  }

  private Expr parseUnary() {
    if (cursor.current() instanceof Token.Minus(var loc)) {
      cursor.advance();
      return new UnaryOp(Node.UnaryOperator.NEG, parsePower(), loc);
    }
    if (cursor.current() instanceof Token.Plus(var ignored)) {
      cursor.advance();
      return parsePower();
    }
    return parsePower();
  }

  private Expr parsePower() {
    var base = parsePostfix();
    if (cursor.current() instanceof Token.Power(var ignored)) {
      cursor.advance();
      var exp = parseUnary(); // right-associative
      return new BinOp(base, Node.BinOperator.POW, exp, base.location());
    }
    return base;
  }

  // ---- Postfix (attribute / index / call / filter) ----

  private Expr parsePostfix() {
    var expr = parsePrimary();
    while (true) {
      var next = applyPostfix(expr);
      if (next == null) return expr;
      expr = next;
    }
  }

  private Expr applyPostfix(Expr expr) {
    var tok = cursor.current();
    if (tok instanceof Token.Dot(var ignored)) {
      cursor.advance();
      // Dotted integer lookup: foo.0 — index into a sequence (Jinja/minijinja).
      if (cursor.current() instanceof Token.IntegerLiteral(long index, SourceLocation idxLoc)) {
        cursor.advance();
        return new GetItem(expr, new Literal(Value.of(index), idxLoc), expr.location());
      }
      var attrName = cursor.expectIdentifierName("attribute name");
      return new GetAttr(expr, attrName, expr.location());
    }
    if (tok instanceof Token.LBracket(var ignored)) {
      return parseIndexingOrSlice(expr);
    }
    if (tok instanceof Token.LParen(var ignored)) {
      cursor.advance();
      var args = new ArrayList<Expr>();
      var kwargs = new LinkedHashMap<String, Expr>();
      parseArgList(args, kwargs);
      cursor.expect(Token.RParen.class, ")");
      return new Call(expr, args, kwargs, expr.location());
    }
    if (tok instanceof Token.Pipe(var ignored)) {
      cursor.advance();
      var filterName = cursor.expectIdentifierName("filter name");
      var args = new ArrayList<Expr>();
      var kwargs = new LinkedHashMap<String, Expr>();
      if (cursor.check(Token.LParen.class)) {
        cursor.advance();
        parseArgList(args, kwargs);
        cursor.expect(Token.RParen.class, ")");
      }
      return new Filter(expr, filterName, args, kwargs, expr.location());
    }
    return null;
  }

  private Expr parseIndexingOrSlice(Expr expr) {
    cursor.advance(); // skip '['
    // Bare slice: [ : stop : step ]
    if (cursor.current() instanceof Token.Colon(var ignored)) {
      return parseSlice(expr, null);
    }
    var key = parseExpression();
    if (cursor.current() instanceof Token.Colon(var ignored)) {
      return parseSlice(expr, key);
    }
    cursor.expect(Token.RBracket.class, "]");
    return new GetItem(expr, key, expr.location());
  }

  private Expr parseSlice(Expr object, Expr start) {
    cursor.advance(); // skip ':'
    Expr stop = null;
    Expr step = null;
    if (!cursor.check(Token.RBracket.class) && !cursor.check(Token.Colon.class)) {
      stop = parseExpression();
    }
    if (cursor.current() instanceof Token.Colon(var ignored)) {
      cursor.advance();
      if (!cursor.check(Token.RBracket.class)) {
        step = parseExpression();
      }
    }
    cursor.expect(Token.RBracket.class, "]");
    return new Slice(object, start, stop, step, object.location());
  }

  /** Populate positional {@code args} and keyword {@code kwargs} from a comma-separated list. */
  void parseArgList(List<Expr> args, Map<String, Expr> kwargs) {
    while (!cursor.check(Token.RParen.class)) {
      if (!args.isEmpty() || !kwargs.isEmpty()) {
        cursor.expect(Token.Comma.class, ",");
        if (cursor.check(Token.RParen.class)) break; // trailing comma
      }
      if (
        cursor.current() instanceof Token.Identifier(String name, var ignored) &&
        cursor.peekAhead(1) instanceof Token.Assign(var ignored2)
      ) {
        cursor.advance(); // skip name
        cursor.advance(); // skip =
        kwargs.put(name, parseExpression());
      } else {
        args.add(parseExpression());
      }
    }
  }

  // ---- Primary (atom) ----

  private Expr parsePrimary() {
    var tok = cursor.current();
    return switch (tok) {
      case Token.StringLiteral(String s, SourceLocation loc) -> parseStringLiteral(s, loc);
      case Token.IntegerLiteral(long i, SourceLocation loc) -> consumeAs(new Literal(Value.of(i), loc));
      case Token.FloatLiteral(double f, SourceLocation loc) -> consumeAs(new Literal(Value.of(f), loc));
      case Token.BooleanLiteral(boolean b, SourceLocation loc) -> consumeAs(new Literal(Value.of(b), loc));
      case Token.NoneLiteral(SourceLocation loc) -> consumeAs(new Literal(Value.NULL, loc));
      case Token.Identifier(String name, SourceLocation loc) -> consumeAs(new Name(name, loc));
      case Token.LParen(SourceLocation loc) -> parseParenthesizedOrTuple(loc);
      case Token.LBracket(SourceLocation loc) -> parseListLiteral(loc);
      case Token.LBrace(SourceLocation loc) -> parseDictLiteral(loc);
      default -> throw new TemplateException(
        "Unexpected token %s in expression".formatted(TokenDescriber.describe(tok)),
        tok.location()
      );
    };
  }

  /**
   * Consume a string literal, then — matching Python/Jinja2 implicit string
   * concatenation — keep consuming adjacent string literals ({@code "a" "b"})
   * and fold them into a single constant.
   */
  private Expr parseStringLiteral(String first, SourceLocation loc) {
    cursor.advance();
    if (!(cursor.current() instanceof Token.StringLiteral)) {
      return new Literal(Value.of(first), loc);
    }
    var sb = new StringBuilder(first);
    while (cursor.current() instanceof Token.StringLiteral(String next, SourceLocation ignored)) {
      sb.append(next);
      cursor.advance();
    }
    return new Literal(Value.of(sb.toString()), loc);
  }

  /** Advance past the current token and return {@code expr}. */
  private Expr consumeAs(Expr expr) {
    cursor.advance();
    return expr;
  }

  private Expr parseParenthesizedOrTuple(SourceLocation loc) {
    cursor.advance(); // skip '('
    if (cursor.check(Token.RParen.class)) {
      cursor.advance();
      return new TupleLiteral(List.of(), loc);
    }
    var first = parseExpression();
    if (!cursor.check(Token.Comma.class)) {
      cursor.expect(Token.RParen.class, ")");
      return first;
    }
    // Tuple
    var items = new ArrayList<Expr>();
    items.add(first);
    while (cursor.check(Token.Comma.class)) {
      cursor.advance();
      if (cursor.check(Token.RParen.class)) break;
      items.add(parseExpression());
    }
    cursor.expect(Token.RParen.class, ")");
    return new TupleLiteral(items, loc);
  }

  private Expr parseListLiteral(SourceLocation loc) {
    cursor.advance(); // skip '['
    var items = new ArrayList<Expr>();
    while (!cursor.check(Token.RBracket.class)) {
      if (!items.isEmpty()) cursor.expect(Token.Comma.class, ",");
      if (cursor.check(Token.RBracket.class)) break;
      items.add(parseExpression());
    }
    cursor.expect(Token.RBracket.class, "]");
    return new ListLiteral(items, loc);
  }

  private Expr parseDictLiteral(SourceLocation loc) {
    cursor.advance(); // skip '{'
    var entries = new ArrayList<Map.Entry<Expr, Expr>>();
    while (!cursor.check(Token.RBrace.class)) {
      if (!entries.isEmpty()) cursor.expect(Token.Comma.class, ",");
      if (cursor.check(Token.RBrace.class)) break;
      var key = parseExpression();
      cursor.expect(Token.Colon.class, ":");
      var value = parseExpression();
      entries.add(Map.entry(key, value));
    }
    cursor.expect(Token.RBrace.class, "}");
    return new DictLiteral(entries, loc);
  }
}
