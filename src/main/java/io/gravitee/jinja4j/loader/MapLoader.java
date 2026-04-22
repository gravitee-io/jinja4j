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

import java.util.HashMap;
import java.util.Map;

/**
 * Loads templates from an in-memory map of name → source.
 */
public final class MapLoader implements TemplateLoader {

  private final Map<String, String> templates;

  public MapLoader(Map<String, String> templates) {
    this.templates = new HashMap<>(templates);
  }

  public void put(String name, String source) {
    templates.put(name, source);
  }

  @Override
  public String load(String name) {
    return templates.get(name);
  }
}
