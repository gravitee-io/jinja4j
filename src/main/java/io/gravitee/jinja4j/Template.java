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

import io.gravitee.jinja4j.ast.Node;
import io.gravitee.jinja4j.value.Value;
import io.gravitee.jinja4j.value.ValueConverter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A parsed Jinja2 template, ready to be rendered with a context.
 *
 * <pre>{@code
 * var tmpl = env.getTemplate("hello.txt");
 * String result = tmpl.render(Map.of("name", "World"));
 * }</pre>
 */
public final class Template {

  private final String name;
  private final Node.Template ast;
  private final Environment env;

  Template(String name, Node.Template ast, Environment env) {
    this.name = name;
    this.ast = ast;
    this.env = env;
  }

  /**
   * Render this template with the given context map.
   */
  public String render(Map<String, Object> context) {
    var ctx = buildContext(context);
    var interpreter = new Interpreter(env);

    // First pass: collect block overrides if there's an extends
    collectBlocks(ast, ctx);

    return interpreter.render(ast, ctx);
  }

  /**
   * Render this template with no context.
   */
  public String render() {
    return render(Map.of());
  }

  /**
   * Render this template with a fluent context builder.
   */
  public String render(Context context) {
    return render(context.toMap());
  }

  public String getName() {
    return name;
  }

  public Node.Template getAst() {
    return ast;
  }

  private RenderContext buildContext(Map<String, Object> context) {
    var ctx = new RenderContext();
    // Import globals first
    ctx.importAll(env.getGlobals());
    // Then user context (overrides globals)
    for (var entry : context.entrySet()) {
      ctx.set(entry.getKey(), ValueConverter.convert(entry.getValue()));
    }
    return ctx;
  }

  private void collectBlocks(Node.Template template, RenderContext ctx) {
    for (var node : template.body()) {
      if (node instanceof Node.BlockNode(var blockName, var body, var ignoredRequired, _)) {
        ctx.setBlock(blockName, body);
      }
    }
  }
}
