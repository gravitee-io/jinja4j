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
package io.gravitee.jinja4j.value;

import java.util.*;

/**
 * The core value type for the template engine. All template values are
 * represented as one of these sealed variants, enabling exhaustive pattern
 * matching in switch expressions.
 */
public sealed interface Value {
  Value UNDEFINED = new Undefined();
  Value NULL = new NullVal();
  Value TRUE = new BoolVal(true);
  Value FALSE = new BoolVal(false);

  record Undefined() implements Value {}

  record NullVal() implements Value {}

  record BoolVal(boolean value) implements Value {}

  record IntVal(long value) implements Value {}

  record FloatVal(double value) implements Value {}

  record StringVal(String value) implements Value {}

  record ListVal(List<Value> items) implements Value {}

  record MapVal(Map<String, Value> entries) implements Value {}

  record CallableVal(String name, TemplateFunction fn) implements Value {}

  record NamespaceVal(Namespace ns) implements Value {}

  record SafeStringVal(String value) implements Value {}

  // ---- Conversion helpers ----

  default String asString() {
    return switch (this) {
      case StringVal sv -> sv.value();
      case SafeStringVal ssv -> ssv.value();
      case IntVal iv -> Long.toString(iv.value());
      case FloatVal fv -> Double.toString(fv.value());
      case BoolVal bv -> bv.value() ? "True" : "False";
      case NullVal ignored -> "";
      case Undefined ignored -> "";
      case ListVal lv -> lv.items().toString();
      case MapVal mv -> mv.entries().toString();
      case CallableVal cv -> "<function %s>".formatted(cv.name());
      case NamespaceVal nv -> "Namespace(%s)".formatted(nv.ns().attrs());
    };
  }

  default boolean isTruthy() {
    return switch (this) {
      case Undefined ignored -> false;
      case NullVal ignored -> false;
      case BoolVal bv -> bv.value();
      case IntVal iv -> iv.value() != 0;
      case FloatVal fv -> fv.value() != 0.0;
      case StringVal sv -> !sv.value().isEmpty();
      case SafeStringVal ssv -> !ssv.value().isEmpty();
      case ListVal lv -> !lv.items().isEmpty();
      case MapVal mv -> !mv.entries().isEmpty();
      case CallableVal ignored -> true;
      case NamespaceVal ignored -> true;
    };
  }

  default boolean isUndefined() {
    return this instanceof Undefined;
  }

  default boolean isNull() {
    return this instanceof NullVal;
  }

  default long asLong() {
    return switch (this) {
      case IntVal iv -> iv.value();
      case FloatVal fv -> (long) fv.value();
      case BoolVal bv -> bv.value() ? 1L : 0L;
      case StringVal sv -> Long.parseLong(sv.value().strip());
      default -> throw new IllegalArgumentException("Cannot convert %s to long".formatted(this));
    };
  }

  default double asDouble() {
    return switch (this) {
      case FloatVal fv -> fv.value();
      case IntVal iv -> (double) iv.value();
      case BoolVal bv -> bv.value() ? 1.0 : 0.0;
      case StringVal sv -> Double.parseDouble(sv.value().strip());
      default -> throw new IllegalArgumentException("Cannot convert %s to double".formatted(this));
    };
  }

  default boolean isNumber() {
    return this instanceof IntVal || this instanceof FloatVal;
  }

  default boolean isString() {
    return this instanceof StringVal || this instanceof SafeStringVal;
  }

  default boolean isSequence() {
    return this instanceof ListVal;
  }

  default boolean isMapping() {
    return this instanceof MapVal || this instanceof NamespaceVal;
  }

  default boolean isCallable() {
    return this instanceof CallableVal;
  }

  default boolean isBoolean() {
    return this instanceof BoolVal;
  }

  default boolean isInteger() {
    return this instanceof IntVal;
  }

  default boolean isFloat() {
    return this instanceof FloatVal;
  }

  // Convenience constructors
  static Value of(String s) {
    return s == null ? NULL : new StringVal(s);
  }

  static Value of(long i) {
    return new IntVal(i);
  }

  static Value of(double f) {
    return new FloatVal(f);
  }

  static Value of(boolean b) {
    return b ? TRUE : FALSE;
  }

  static Value ofList(List<Value> items) {
    return new ListVal(items);
  }

  static Value ofMap(Map<String, Value> entries) {
    return new MapVal(entries);
  }

  static Value ofCallable(String name, TemplateFunction fn) {
    return new CallableVal(name, fn);
  }
}
