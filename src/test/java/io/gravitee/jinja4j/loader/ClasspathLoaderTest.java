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

import org.junit.jupiter.api.Test;

class ClasspathLoaderTest {

  @Test
  void loadsResourceFromPrefixedDirectory() {
    var loader = new ClasspathLoader("templates/");
    assertThat(loader.load("synthetic-simple.jinja")).isNotNull();
  }

  @Test
  void noArgConstructorLoadsFromClasspathRoot() {
    var loader = new ClasspathLoader();
    assertThat(loader.load("templates/synthetic-simple.jinja")).isNotNull();
  }

  @Test
  void noArgConstructorReturnsNullForMissingResource() {
    var loader = new ClasspathLoader();
    assertThat(loader.load("nonexistent-file.txt")).isNull();
  }

  @Test
  void returnsNullForMissingResourceWithPrefix() {
    var loader = new ClasspathLoader("templates");
    assertThat(loader.load("does-not-exist.jinja")).isNull();
  }
}
