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

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link Value}: its factories, type predicates and
 * {@code asXxx} conversions across every variant.
 */
class ValueTest {

  // ---- asString ----

  @Test
  void asStringRendersStringValueAsIs() {
    assertThat(Value.of("hello").asString()).isEqualTo("hello");
    assertThat(new Value.SafeStringVal("safe").asString()).isEqualTo("safe");
  }

  @Test
  void asStringRendersNumericTypes() {
    assertThat(Value.of(42L).asString()).isEqualTo("42");
    assertThat(Value.of(0L).asString()).isEqualTo("0");
    assertThat(Value.of(-5L).asString()).isEqualTo("-5");
    assertThat(Value.of(3.14).asString()).isEqualTo("3.14");
    assertThat(Value.of(0.0).asString()).isEqualTo("0.0");
    assertThat(Value.of(-1.5).asString()).isEqualTo("-1.5");
  }

  @Test
  void asStringRendersBooleansWithPythonCasing() {
    assertThat(Value.of(true).asString()).isEqualTo("True");
    assertThat(Value.of(false).asString()).isEqualTo("False");
    assertThat(new Value.BoolVal(true).asString()).isEqualTo("True");
    assertThat(new Value.BoolVal(false).asString()).isEqualTo("False");
  }

  @Test
  void asStringRendersNullAndUndefinedAsEmpty() {
    assertThat(Value.NULL.asString()).isEqualTo("");
    assertThat(Value.UNDEFINED.asString()).isEqualTo("");
  }

  @Test
  void asStringRendersCollectionsAndCallables() {
    assertThat(Value.ofList(List.of(Value.of(1L), Value.of(2L))).asString()).contains("1").contains("2");
    assertThat(Value.ofMap(Map.of("k", Value.of("v"))).asString()).contains("k").contains("v");
    assertThat(Value.ofCallable("myFunc", (a, k) -> Value.NULL).asString()).contains("myFunc");
    assertThat(new Value.NamespaceVal(new Namespace(Map.of("x", Value.of(1L)))).asString()).contains("Namespace");
  }

  // ---- isTruthy ----

  @Test
  void undefinedAndNullAreFalsy() {
    assertThat(Value.UNDEFINED.isTruthy()).isFalse();
    assertThat(Value.NULL.isTruthy()).isFalse();
  }

  @Test
  void booleanTruthinessReflectsValue() {
    assertThat(Value.of(true).isTruthy()).isTrue();
    assertThat(Value.of(false).isTruthy()).isFalse();
  }

  @Test
  void numericZeroIsFalsyAndNonZeroIsTruthy() {
    assertThat(new Value.IntVal(0L).isTruthy()).isFalse();
    assertThat(new Value.IntVal(1L).isTruthy()).isTrue();
    assertThat(new Value.IntVal(-1L).isTruthy()).isTrue();
    assertThat(new Value.FloatVal(0.0).isTruthy()).isFalse();
    assertThat(new Value.FloatVal(1.5).isTruthy()).isTrue();
    assertThat(new Value.FloatVal(-0.1).isTruthy()).isTrue();
  }

  @Test
  void emptyStringIsFalsyAndNonEmptyIsTruthy() {
    assertThat(Value.of("").isTruthy()).isFalse();
    assertThat(Value.of("x").isTruthy()).isTrue();
    assertThat(new Value.SafeStringVal("").isTruthy()).isFalse();
    assertThat(new Value.SafeStringVal("x").isTruthy()).isTrue();
  }

  @Test
  void emptyCollectionsAreFalsyAndNonEmptyAreTruthy() {
    assertThat(Value.ofList(List.of()).isTruthy()).isFalse();
    assertThat(Value.ofList(List.of(Value.of(1L))).isTruthy()).isTrue();
    assertThat(Value.ofMap(Map.of()).isTruthy()).isFalse();
    assertThat(Value.ofMap(Map.of("a", Value.of(1L))).isTruthy()).isTrue();
  }

  @Test
  void callablesAndNamespacesAreTruthy() {
    assertThat(Value.ofCallable("fn", (a, k) -> Value.NULL).isTruthy()).isTrue();
    assertThat(new Value.NamespaceVal(new Namespace(Map.of())).isTruthy()).isTrue();
  }

  // ---- asLong / asDouble ----

  @Test
  void asLongSupportsIntFloatBoolAndString() {
    assertThat(Value.of(42L).asLong()).isEqualTo(42L);
    assertThat(Value.of(-5L).asLong()).isEqualTo(-5L);
    assertThat(Value.of(3.9).asLong()).isEqualTo(3L);
    assertThat(Value.of(-3.9).asLong()).isEqualTo(-3L);
    assertThat(Value.of(true).asLong()).isEqualTo(1L);
    assertThat(Value.of(false).asLong()).isEqualTo(0L);
    assertThat(Value.of("42").asLong()).isEqualTo(42L);
  }

  @Test
  void asDoubleSupportsIntFloatBoolAndString() {
    assertThat(Value.of(42L).asDouble()).isEqualTo(42.0);
    assertThat(Value.of(3.14).asDouble()).isEqualTo(3.14);
    assertThat(Value.of(true).asDouble()).isEqualTo(1.0);
    assertThat(Value.of(false).asDouble()).isEqualTo(0.0);
    assertThat(Value.of("2.5").asDouble()).isEqualTo(2.5);
  }

  @Test
  void asLongRejectsNonConvertibleTypes() {
    assertThatThrownBy(() -> Value.ofList(List.of()).asLong()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.ofMap(Map.of()).asLong()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.NULL.asLong()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.UNDEFINED.asLong()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Value.SafeStringVal("x").asLong()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.ofCallable("fn", (a, k) -> Value.NULL).asLong()).isInstanceOf(
      IllegalArgumentException.class
    );
    assertThatThrownBy(() -> new Value.NamespaceVal(new Namespace(Map.of())).asLong()).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  @Test
  void asDoubleRejectsNonConvertibleTypes() {
    assertThatThrownBy(() -> Value.ofList(List.of()).asDouble()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.ofMap(Map.of()).asDouble()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.NULL.asDouble()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.UNDEFINED.asDouble()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Value.SafeStringVal("x").asDouble()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Value.ofCallable("fn", (a, k) -> Value.NULL).asDouble()).isInstanceOf(
      IllegalArgumentException.class
    );
    assertThatThrownBy(() -> new Value.NamespaceVal(new Namespace(Map.of())).asDouble()).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  // ---- type predicates ----

  @Test
  void numericPredicates() {
    assertThat(Value.of(1L).isInteger()).isTrue();
    assertThat(Value.of(1L).isFloat()).isFalse();
    assertThat(Value.of(1.0).isInteger()).isFalse();
    assertThat(Value.of(1.0).isFloat()).isTrue();
    assertThat(Value.of(1L).isNumber()).isTrue();
    assertThat(Value.of(1.0).isNumber()).isTrue();
    assertThat(Value.of("x").isNumber()).isFalse();
  }

  @Test
  void stringPredicate() {
    assertThat(Value.of("x").isString()).isTrue();
    assertThat(new Value.SafeStringVal("x").isString()).isTrue();
    assertThat(Value.of(1L).isString()).isFalse();
  }

  @Test
  void collectionPredicates() {
    assertThat(Value.ofList(List.of()).isSequence()).isTrue();
    assertThat(Value.of("x").isSequence()).isFalse();
    assertThat(Value.ofMap(Map.of()).isMapping()).isTrue();
    assertThat(new Value.NamespaceVal(new Namespace(Map.of())).isMapping()).isTrue();
    assertThat(Value.of("x").isMapping()).isFalse();
  }

  @Test
  void callableAndBooleanPredicates() {
    assertThat(Value.ofCallable("fn", (a, k) -> Value.NULL).isCallable()).isTrue();
    assertThat(Value.of("x").isCallable()).isFalse();
    assertThat(Value.of(true).isBoolean()).isTrue();
    assertThat(Value.of(1L).isBoolean()).isFalse();
  }

  @Test
  void nullAndUndefinedPredicates() {
    assertThat(Value.NULL.isNull()).isTrue();
    assertThat(Value.of(1L).isNull()).isFalse();
    assertThat(Value.UNDEFINED.isUndefined()).isTrue();
    assertThat(Value.of(1L).isUndefined()).isFalse();
  }

  // ---- factories ----

  @Test
  void ofNullStringReturnsNullValue() {
    assertThat(Value.of((String) null)).isEqualTo(Value.NULL);
  }
}
