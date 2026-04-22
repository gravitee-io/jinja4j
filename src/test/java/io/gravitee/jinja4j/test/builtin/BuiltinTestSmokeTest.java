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
package io.gravitee.jinja4j.test.builtin;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.test.NamedTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Smoke tests verifying every builtin test class is a singleton with the expected name/aliases. */
class BuiltinTestSmokeTest {

  @Test
  void namesMatchExpected() {
    assertThat(DefinedTest.INSTANCE.name()).isEqualTo("defined");
    assertThat(UndefinedTest.INSTANCE.name()).isEqualTo("undefined");
    assertThat(NoneTest.INSTANCE.name()).isEqualTo("none");
    assertThat(BooleanTest.INSTANCE.name()).isEqualTo("boolean");
    assertThat(IntegerTest.INSTANCE.name()).isEqualTo("integer");
    assertThat(FloatTypeTest.INSTANCE.name()).isEqualTo("float");
    assertThat(NumberTest.INSTANCE.name()).isEqualTo("number");
    assertThat(StringTest.INSTANCE.name()).isEqualTo("string");
    assertThat(SequenceTest.INSTANCE.name()).isEqualTo("sequence");
    assertThat(MappingTest.INSTANCE.name()).isEqualTo("mapping");
    assertThat(IterableTest.INSTANCE.name()).isEqualTo("iterable");
    assertThat(CallableTest.INSTANCE.name()).isEqualTo("callable");
    assertThat(OddTest.INSTANCE.name()).isEqualTo("odd");
    assertThat(EvenTest.INSTANCE.name()).isEqualTo("even");
    assertThat(DivisiblebyTest.INSTANCE.name()).isEqualTo("divisibleby");
    assertThat(SameasTest.INSTANCE.name()).isEqualTo("sameas");
    assertThat(EqTest.INSTANCE.name()).isEqualTo("eq");
    assertThat(NeTest.INSTANCE.name()).isEqualTo("ne");
    assertThat(LtTest.INSTANCE.name()).isEqualTo("lt");
    assertThat(LeTest.INSTANCE.name()).isEqualTo("le");
    assertThat(GtTest.INSTANCE.name()).isEqualTo("gt");
    assertThat(GeTest.INSTANCE.name()).isEqualTo("ge");
    assertThat(TrueTest.INSTANCE.name()).isEqualTo("true");
    assertThat(FalseTest.INSTANCE.name()).isEqualTo("false");
    assertThat(LowerTest.INSTANCE.name()).isEqualTo("lower");
    assertThat(UpperTest.INSTANCE.name()).isEqualTo("upper");
  }

  @Test
  void aliasesDeclared() {
    assertThat(EqTest.INSTANCE.aliases()).containsExactly("equalto", "==");
    assertThat(LtTest.INSTANCE.aliases()).containsExactly("lessthan");
    assertThat(GtTest.INSTANCE.aliases()).containsExactly("greaterthan");
  }

  @Test
  void testsWithoutAliasesReturnEmpty() {
    List<NamedTest> noAliasTests = List.of(
      DefinedTest.INSTANCE,
      UndefinedTest.INSTANCE,
      NoneTest.INSTANCE,
      OddTest.INSTANCE,
      EvenTest.INSTANCE,
      TrueTest.INSTANCE,
      FalseTest.INSTANCE
    );
    for (var t : noAliasTests) {
      assertThat(t.aliases()).as("%s aliases", t.name()).isEmpty();
    }
  }

  @Test
  void singletonsAreStable() {
    assertThat(DefinedTest.INSTANCE).isSameAs(DefinedTest.INSTANCE);
    assertThat(EqTest.INSTANCE).isSameAs(EqTest.INSTANCE);
    assertThat(OddTest.INSTANCE).isSameAs(OddTest.INSTANCE);
  }
}
