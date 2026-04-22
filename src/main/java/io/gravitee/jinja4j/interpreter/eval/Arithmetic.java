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
package io.gravitee.jinja4j.interpreter.eval;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.value.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure arithmetic and string-sequence operators on {@link Value}.
 *
 * <p>The dispatch rules:</p>
 * <ul>
 *   <li>{@link #add} also handles string concatenation and list concatenation.</li>
 *   <li>{@link #mul} also handles string repetition.</li>
 *   <li>Integer operands stay integer when both sides are {@code IntVal}; otherwise
 *       the operation promotes to {@code double} via {@link Value#asDouble()}.</li>
 *   <li>Division always returns a {@code double}, matching Jinja2.</li>
 *   <li>{@link #pow} stays integer only when the exponent is a non-negative int.</li>
 * </ul>
 */
public final class Arithmetic {

  private Arithmetic() {}

  public static Value add(Value left, Value right, SourceLocation loc) {
    if (left instanceof Value.StringVal(String a) && right instanceof Value.StringVal(String b)) {
      return Value.of(a + b);
    }
    if (left instanceof Value.ListVal(List<Value> a) && right instanceof Value.ListVal(List<Value> b)) {
      var combined = new ArrayList<>(a);
      combined.addAll(b);
      return Value.ofList(combined);
    }
    return numeric(left, right, Long::sum, Double::sum, loc);
  }

  public static Value sub(Value left, Value right, SourceLocation loc) {
    return numeric(left, right, (a, b) -> a - b, (a, b) -> a - b, loc);
  }

  public static Value mul(Value left, Value right, SourceLocation loc) {
    if (left instanceof Value.StringVal(String s) && right instanceof Value.IntVal(long n)) {
      return Value.of(s.repeat((int) n));
    }
    if (left instanceof Value.IntVal(long n) && right instanceof Value.StringVal(String s)) {
      return Value.of(s.repeat((int) n));
    }
    return numeric(left, right, (a, b) -> a * b, (a, b) -> a * b, loc);
  }

  /** Division always promotes to double, matching Jinja2's {@code /} semantics. */
  public static Value div(Value left, Value right) {
    return Value.of(left.asDouble() / right.asDouble());
  }

  public static Value floorDiv(Value left, Value right) {
    if (left instanceof Value.IntVal(long a) && right instanceof Value.IntVal(long b)) {
      return Value.of(Math.floorDiv(a, b));
    }
    return Value.of((long) Math.floor(left.asDouble() / right.asDouble()));
  }

  public static Value mod(Value left, Value right) {
    if (left instanceof Value.IntVal(long a) && right instanceof Value.IntVal(long b)) {
      return Value.of(Math.floorMod(a, b));
    }
    return Value.of(left.asDouble() % right.asDouble());
  }

  public static Value pow(Value left, Value right) {
    if (left instanceof Value.IntVal(long a) && right instanceof Value.IntVal(long b) && b >= 0) {
      return Value.of((long) Math.pow(a, b));
    }
    return Value.of(Math.pow(left.asDouble(), right.asDouble()));
  }

  // ---- Numeric dispatch ----

  @FunctionalInterface
  private interface LongBiOp {
    long apply(long a, long b);
  }

  @FunctionalInterface
  private interface DoubleBiOp {
    double apply(double a, double b);
  }

  private static Value numeric(Value left, Value right, LongBiOp longOp, DoubleBiOp doubleOp, SourceLocation loc) {
    if (left instanceof Value.IntVal(long a) && right instanceof Value.IntVal(long b)) {
      return Value.of(longOp.apply(a, b));
    }
    try {
      return Value.of(doubleOp.apply(left.asDouble(), right.asDouble()));
    } catch (Exception e) {
      throw new TemplateException("Cannot perform arithmetic on %s and %s".formatted(left, right), loc);
    }
  }
}
