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
package io.gravitee.jinja4j.loader;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemLoaderTest {

  @Test
  void loadsTemplateFromDirectory(@TempDir Path tempDir) throws Exception {
    Files.writeString(tempDir.resolve("test.txt"), "Hello {{ name }}!");
    var env = new Environment();
    env.addLoader(new FileSystemLoader(tempDir));
    var tmpl = env.getTemplate("test.txt");
    assertThat(tmpl.render(Map.of("name", "World"))).isEqualTo("Hello World!");
  }

  @Test
  void acceptsStringPathInConstructor(@TempDir Path tempDir) throws Exception {
    Files.writeString(tempDir.resolve("test.txt"), "Hi!");
    var loader = new FileSystemLoader(tempDir.toString());
    assertThat(loader.load("test.txt")).isEqualTo("Hi!");
  }

  @Test
  void returnsNullForMissingFile(@TempDir Path tempDir) {
    var loader = new FileSystemLoader(tempDir);
    assertThat(loader.load("nonexistent.txt")).isNull();
  }
}
