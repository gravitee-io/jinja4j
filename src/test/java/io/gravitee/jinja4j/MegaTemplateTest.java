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

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.loader.ClasspathLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Snapshot test for {@code templates/all-builtins.jinja}, a single template
 * that invokes every built-in filter and every built-in test at least once.
 *
 * <p>The template renders to a deterministic report; the expected rendering
 * is stored alongside the template in {@code all-builtins.expected.txt}.</p>
 *
 * <p>When a filter or test is added/changed, regenerate the expected file by
 * running this test with {@code -Djinja4j.snapshot.update=true}:</p>
 * <pre>mvn test -Dtest=MegaTemplateTest -Djinja4j.snapshot.update=true</pre>
 */
class MegaTemplateTest {

  private static final String EXPECTED_RESOURCE = "templates/all-builtins.expected.txt";
  private static final String UPDATE_PROPERTY = "jinja4j.snapshot.update";

  @Test
  void rendersExpectedReport() throws IOException {
    var env = new Environment();
    env.addLoader(new ClasspathLoader("templates"));
    var tmpl = env.getTemplate("all-builtins.jinja");

    var actual = tmpl.render(buildContext());

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      writeExpectedFile(actual);
      return;
    }

    var expected = readExpected();
    assertThat(actual).isEqualTo(expected);
  }

  // ---------------------------------------------------------------------------

  private static Map<String, Object> buildContext() {
    var ordered = new LinkedHashMap<String, Object>();
    ordered.put("a", 1);
    ordered.put("b", 2);
    ordered.put("c", 3);

    var people = new ArrayList<Map<String, Object>>();
    people.add(orderedMap("name", "Alice", "team", "red", "active", true));
    people.add(orderedMap("name", "Bob", "team", "blue", "active", false));
    people.add(orderedMap("name", "Carol", "team", "red", "active", true));

    var ctx = new HashMap<String, Object>();
    ctx.put("ordered", ordered);
    ctx.put("people", people);
    ctx.put("nullable", null);
    return ctx;
  }

  private static Map<String, Object> orderedMap(Object... kv) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private static String readExpected() throws IOException {
    try (var in = MegaTemplateTest.class.getClassLoader().getResourceAsStream(EXPECTED_RESOURCE)) {
      assertThat(in)
        .as("Expected snapshot %s is missing. Regenerate with -D%s=true", EXPECTED_RESOURCE, UPDATE_PROPERTY)
        .isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void writeExpectedFile(String content) throws IOException {
    var target = Path.of("src", "test", "resources", EXPECTED_RESOURCE);
    Files.writeString(target, content, StandardCharsets.UTF_8);
  }
}
