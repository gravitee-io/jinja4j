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

import org.junit.jupiter.api.Test;

class TemplateExceptionTest {

  @Test
  void carriesTemplateLocationMetadata() {
    var ex = new TemplateException("bad syntax", new SourceLocation("test.html", 5, 10));
    assertThat(ex.getTemplateName()).isEqualTo("test.html");
    assertThat(ex.getLine()).isEqualTo(5);
    assertThat(ex.getColumn()).isEqualTo(10);
    assertThat(ex.getDetail()).isEqualTo("bad syntax");
    assertThat(ex.getMessage()).contains("test.html").contains("line 5").contains("column 10");
  }

  @Test
  void preservesCauseWhenProvided() {
    var cause = new RuntimeException("root cause");
    var ex = new TemplateException("error", new SourceLocation("x.html", 1, 1), cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void omitsLocationWhenUnknown() {
    var ex = new TemplateException("error", SourceLocation.UNKNOWN);
    assertThat(ex.getMessage()).isEqualTo("error").doesNotContain("line");
  }
}
