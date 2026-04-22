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

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.TemplateException;
import org.junit.jupiter.api.Test;

/**
 * Parser error paths: unknown tags, malformed expressions, non-callable values.
 */
class ParserErrorTest {

  private final Environment env = new Environment();

  @Test
  void nonIdentifierAtStatementStart() {
    assertThatThrownBy(() -> env.fromString("{% 123 %}").render()).isInstanceOf(TemplateException.class);
  }

  @Test
  void unknownStatementTag() {
    assertThatThrownBy(() -> env.fromString("{% foobar %}").render()).isInstanceOf(TemplateException.class);
  }

  @Test
  void includeWithIgnoreFollowedByWrongWord() {
    assertThatThrownBy(() -> env.fromString("{% include 'x.txt' ignore foobar %}").render()).isInstanceOf(
      TemplateException.class
    );
  }

  @Test
  void includeOfMissingTemplateWithoutIgnore() {
    assertThatThrownBy(() -> env.fromString("{% include 'missing.txt' %}").render()).isInstanceOf(TemplateException.class);
  }

  @Test
  void extendsOfMissingParent() {
    assertThatThrownBy(() -> env.fromString("{% extends 'nonexistent.html' %}").render()).isInstanceOf(
      TemplateException.class
    );
  }
}
