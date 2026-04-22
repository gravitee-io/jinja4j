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

import io.gravitee.jinja4j.Environment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ValueConverter}: Java-to-Value and Value-to-Java round-trips,
 * including primitives, arrays, POJOs and records.
 */
class ValueConverterTest {

  // ---- fixtures ----

  public static class SimplePojo {

    private final String name;
    private final int age;
    private final boolean active;

    public SimplePojo(String name, int age, boolean active) {
      this.name = name;
      this.age = age;
      this.active = active;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }

    public boolean isActive() {
      return active;
    }
  }

  public record SimpleRecord(String name, int value) {}

  public static class PojoWithIsBoolean {

    public boolean isActive() {
      return true;
    }

    public String getName() {
      return "name-val";
    }

    public String getJustGet() {
      return "get";
    }
  }

  public static class PojoWithNonBooleanIs {

    public String isFoo() {
      return "foo"; // not boolean return — should be skipped
    }

    public String getBar() {
      return "bar";
    }
  }

  public static class PojoWithJustIsPrefix {

    public boolean is() {
      return true; // method name is exactly "is" — not a boolean getter
    }

    public String getName() {
      return "n";
    }
  }

  private final Environment env = new Environment();

  // ---- Java → Value ----

  @Test
  void convertsPojoGettersAndIsProperties() {
    var pojo = new SimplePojo("Alice", 30, true);
    assertThat(env.fromString("{{ p.name }}|{{ p.age }}|{{ p.active }}").render(Map.of("p", pojo))).isEqualTo(
      "Alice|30|True"
    );
  }

  @Test
  void convertsRecordAccessors() {
    assertThat(env.fromString("{{ r.name }}={{ r.value }}").render(Map.of("r", new SimpleRecord("k", 42)))).isEqualTo(
      "k=42"
    );
  }

  @Test
  void convertsNullToNullValue() {
    var ctx = new HashMap<String, Object>();
    ctx.put("x", null);
    assertThat(env.fromString("{{ x }}").render(ctx)).isEqualTo("");
  }

  @Test
  void convertsPrimitiveNumericTypes() {
    short s = 5;
    byte b = 3;
    float f = 2.5f;
    assertThat(env.fromString("{{ s }}+{{ b }}+{{ f }}").render(Map.of("s", s, "b", b, "f", f))).isEqualTo("5+3+2.5");
  }

  @Test
  void convertsObjectArray() {
    Object[] arr = { "a", "b", "c" };
    assertThat(env.fromString("{{ items | join(', ') }}").render(Map.of("items", arr))).isEqualTo("a, b, c");
  }

  @Test
  void convertsPojoWithIsBooleanGetter() {
    var p = new PojoWithIsBoolean();
    assertThat(env.fromString("{{ p.active }} {{ p.name }}").render(Map.of("p", p))).isEqualTo("True name-val");
  }

  @Test
  void skipsIsPrefixedMethodsThatDoNotReturnBoolean() {
    var p = new PojoWithNonBooleanIs();
    assertThat(env.fromString("{{ p.bar }}").render(Map.of("p", p))).isEqualTo("bar");
  }

  @Test
  void doesNotTreatMethodNamedExactlyIsAsGetter() {
    var p = new PojoWithJustIsPrefix();
    assertThat(env.fromString("{{ p.name }}").render(Map.of("p", p))).isEqualTo("n");
  }

  @Test
  void convertsLongDirectly() {
    assertThat(ValueConverter.convert(42L)).isEqualTo(Value.of(42L));
  }

  @Test
  void convertsNamespaceViaRecordPath() {
    // Namespace is a record, so it goes through the convertRecord() path (not the Namespace case).
    var ns = new Namespace(Map.of("x", Value.of(1L)));
    assertThat(ValueConverter.convert(ns)).isInstanceOf(Value.MapVal.class);
  }

  @Test
  void convertsSafeStringViaRecordPath() {
    // SafeString is a record — record conversion creates a Map with a "value" key.
    var safe = new SafeString("<b>bold</b>");
    assertThat(ValueConverter.convert(safe)).isInstanceOf(Value.MapVal.class);
  }

  @Test
  void renderingAcceptsValueDirectlyInContext() {
    // Value is an interface with record implementations — goes through convertRecord.
    // The point here is that rendering must succeed, regardless of the conversion path.
    var ctx = new HashMap<String, Object>();
    ctx.put("x", Value.of("already"));
    assertThat(env.fromString("{{ x }}").render(ctx)).isNotNull();
  }

  @Test
  void renderingHandlesSafeStringInContext() {
    var ctx = new HashMap<String, Object>();
    ctx.put("x", new SafeString("<b>bold</b>"));
    env.setAutoEscaping(true);
    assertThat(env.fromString("{{ x.value }}").render(ctx)).contains("bold");
  }

  @Test
  void renderingHandlesNamespaceInContext() {
    var ctx = new HashMap<String, Object>();
    ctx.put("n", new Namespace(Map.of("x", Value.of(42L))));
    assertThat(env.fromString("{{ n }}").render(ctx)).isNotEmpty();
  }

  // ---- Value → Java ----

  @Test
  void toJavaReturnsNullForNullAndUndefined() {
    assertThat(ValueConverter.toJava(Value.NULL)).isNull();
    assertThat(ValueConverter.toJava(Value.UNDEFINED)).isNull();
  }

  @Test
  void toJavaUnwrapsScalarValues() {
    assertThat(ValueConverter.toJava(Value.of(true))).isEqualTo(true);
    assertThat(ValueConverter.toJava(Value.of(42L))).isEqualTo(42L);
    assertThat(ValueConverter.toJava(Value.of(3.14))).isEqualTo(3.14);
    assertThat(ValueConverter.toJava(Value.of("hello"))).isEqualTo("hello");
    assertThat(ValueConverter.toJava(new Value.SafeStringVal("safe"))).isEqualTo("safe");
  }

  @Test
  void toJavaUnwrapsListsAndMaps() {
    var listVal = Value.ofList(List.of(Value.of(1L), Value.of(2L)));
    assertThat(ValueConverter.toJava(listVal)).isEqualTo(List.of(1L, 2L));

    var mapVal = Value.ofMap(Map.of("a", Value.of(1L)));
    @SuppressWarnings("unchecked")
    var javaMap = (Map<String, Object>) ValueConverter.toJava(mapVal);
    assertThat(javaMap).containsEntry("a", 1L);
  }

  @Test
  void toJavaReturnsCallableAndNamespaceAsThemselves() {
    var callable = Value.ofCallable("fn", (args, kw) -> Value.NULL);
    assertThat(ValueConverter.toJava(callable)).isSameAs(callable);

    var ns = new Namespace(Map.of());
    assertThat(ValueConverter.toJava(new Value.NamespaceVal(ns))).isSameAs(ns);
  }
}
