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

/**
 * Thrown when a template cannot be parsed or rendered.
 */
public class TemplateException extends RuntimeException {

  private final String templateName;
  private final int line;
  private final int column;
  private final String detail;

  public TemplateException(String detail, SourceLocation loc) {
    super(formatMessage(detail, loc));
    this.templateName = loc.templateName();
    this.line = loc.line();
    this.column = loc.column();
    this.detail = detail;
  }

  public TemplateException(String detail, SourceLocation loc, Throwable cause) {
    super(formatMessage(detail, loc), cause);
    this.templateName = loc.templateName();
    this.line = loc.line();
    this.column = loc.column();
    this.detail = detail;
  }

  public String getTemplateName() {
    return templateName;
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  public String getDetail() {
    return detail;
  }

  private static String formatMessage(String detail, SourceLocation loc) {
    if (loc == null || loc.equals(SourceLocation.UNKNOWN)) {
      return detail;
    }
    return "%s in template '%s' on line %d, column %d".formatted(detail, loc.templateName(), loc.line(), loc.column());
  }
}
