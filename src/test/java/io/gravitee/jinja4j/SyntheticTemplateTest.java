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

import io.gravitee.jinja4j.chat.Message;
import io.gravitee.jinja4j.loader.ClasspathLoader;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for synthetic chat templates loaded from classpath.
 *
 * <p>Three synthetic templates exercise all Jinja2 idioms found in
 * chat templates:</p>
 * <ul>
 *   <li>synthetic-simple — core control flow, string methods, role dispatch</li>
 *   <li>synthetic-tools — macros, recursion, dict iteration, filters, type tests</li>
 *   <li>synthetic-thinking — namespace mutation, split/strip chains, range, .get()</li>
 * </ul>
 */
class SyntheticTemplateTest {

  private Environment env;

  @BeforeEach
  void setUp() {
    env = new Environment();
    env.addLoader(new ClasspathLoader("templates"));
  }

  private static final List<Message> STANDARD = List.of(
    new Message("system", "You are a helpful assistant."),
    new Message("user", "What is 2+2?")
  );

  private static final List<Message> MULTI_TURN = List.of(
    new Message("system", "Be brief."),
    new Message("user", "Hi"),
    new Message("assistant", "Hello!"),
    new Message("user", "What is 2+2?")
  );

  // ===========================================================================
  // synthetic-simple.jinja
  // ===========================================================================

  @Test
  void simple_basicConversation() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    var result = tmpl.render(ctx(STANDARD, true));

    assertThat(result)
      .startsWith("<BOS>")
      .contains("<|start|>user<|sep|>\nYou are a helpful assistant.\n\nWhat is 2+2?<|end|>")
      .endsWith("<|start|>assistant<|sep|>\n");
  }

  @Test
  void simple_multiTurn() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    var result = tmpl.render(ctx(MULTI_TURN, true));

    assertThat(result)
      .startsWith("<BOS>")
      .contains("<|start|>user<|sep|>\nBe brief.\n\nHi<|end|>")
      .contains("<|start|>assistant<|sep|>\nHello!</s>\n")
      .contains("<|start|>user<|sep|>\nWhat is 2+2?<|end|>")
      .endsWith("<|start|>assistant<|sep|>\n");
  }

  @Test
  void simple_withoutSystemMessage() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    var msgs = List.of(new Message("user", "Hello"));
    var result = tmpl.render(ctx(msgs, true));

    assertThat(result)
      .startsWith("<BOS>")
      .contains("<|start|>user<|sep|>\nHello<|end|>")
      .endsWith("<|start|>assistant<|sep|>\n");
  }

  @Test
  void simple_stripAndReplace() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    // System message with leading/trailing whitespace and \r\n
    var msgs = List.of(new Message("system", "  hello\r\nworld  "), new Message("user", "Hi"));
    var result = tmpl.render(ctx(msgs, false));

    // .strip() removes whitespace, .replace('\r\n', '\n') normalizes line endings
    assertThat(result).contains("hello\nworld\n\nHi");
  }

  @Test
  void simple_roleAlternationEnforced() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    var msgs = List.of(new Message("user", "Hi"), new Message("user", "Again"));
    assertThatThrownBy(() -> tmpl.render(ctx(msgs, false)))
      .isInstanceOf(TemplateException.class)
      .hasMessageContaining("alternate");
  }

  @Test
  void simple_unsupportedRoleUsesTitle() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    // Place unsupported role at odd index to pass alternation check
    var msgs = List.of(new Message("user", "Hi"), new Message("developer", "test"));
    assertThatThrownBy(() -> tmpl.render(ctx(msgs, false)))
      .isInstanceOf(TemplateException.class)
      .hasMessageContaining("Developer");
  }

  @Test
  void simple_noGenerationPrompt() {
    var tmpl = env.getTemplate("synthetic-simple.jinja");
    var msgs = List.of(new Message("user", "Hi"), new Message("assistant", "Hello!"));
    var result = tmpl.render(ctx(msgs, false));

    assertThat(result).endsWith("Hello!</s>\n").doesNotContain("<|start|>assistant<|sep|>\n<|start|>");
  }

  // ===========================================================================
  // synthetic-tools.jinja
  // ===========================================================================

  @Test
  void tools_basicConversation() {
    var tmpl = env.getTemplate("synthetic-tools.jinja");
    var result = tmpl.render(Map.of("messages", messageMaps(STANDARD), "add_generation_prompt", true));

    assertThat(result)
      .contains("<|system|>\nYou are a helpful assistant.\n<|end|>")
      .contains("<|user|>\nWhat is 2+2?\n<|end|>")
      .endsWith("<|assistant|>\n");
  }

  @Test
  void tools_withToolDefinitions() {
    var tmpl = env.getTemplate("synthetic-tools.jinja");
    var tool = Map.of(
      "function",
      Map.of(
        "name",
        "get_weather",
        "description",
        "Get weather for a city",
        "parameters",
        Map.of(
          "type",
          "object",
          "properties",
          Map.<String, Object>of("city", Map.of("type", "string"), "unit", Map.of("enum", List.of("celsius", "fahrenheit"))),
          "required",
          List.of("city")
        )
      )
    );
    var result = tmpl.render(
      Map.of(
        "messages",
        messageMaps(List.of(new Message("user", "Weather in Paris?"))),
        "tools",
        List.of(tool),
        "add_generation_prompt",
        true
      )
    );

    assertThat(result)
      .contains("<|tools|>")
      .contains("<|tool_def|>get_weather(")
      .contains("city: string")
      .contains("unit: celsius | fahrenheit")
      .contains("<|end_tool|>")
      .contains("<|end_tools|>")
      .contains("<|user|>\nWeather in Paris?")
      .endsWith("<|assistant|>\n");
  }

  @Test
  void tools_recursiveTypeRendering() {
    var tmpl = env.getTemplate("synthetic-tools.jinja");
    // Nested object with array of objects — exercises recursive macro
    var tool = Map.of(
      "function",
      Map.of(
        "name",
        "create_order",
        "description",
        "Create an order",
        "parameters",
        Map.of(
          "type",
          "object",
          "properties",
          Map.<String, Object>of(
            "items",
            Map.of(
              "type",
              "array",
              "items",
              Map.of(
                "type",
                "object",
                "properties",
                Map.of("name", Map.of("type", "string"), "qty", Map.of("type", "integer"))
              )
            )
          ),
          "required",
          List.of("items")
        )
      )
    );
    var result = tmpl.render(
      Map.of(
        "messages",
        messageMaps(List.of(new Message("user", "Order pizza"))),
        "tools",
        List.of(tool),
        "add_generation_prompt",
        true
      )
    );

    // The recursive macro should render nested object types
    assertThat(result).contains("items: {name: string, qty: integer}[]");
  }

  @Test
  void tools_toolCallAndResponse() {
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "Weather?"),
      Map.of(
        "role",
        "assistant",
        "content",
        "",
        "tool_calls",
        List.of(Map.of("function", Map.of("name", "get_weather", "arguments", Map.of("city", "Paris"))))
      ),
      Map.of("role", "tool", "name", "get_weather", "content", "22°C sunny")
    );
    var result = env.getTemplate("synthetic-tools.jinja").render(Map.of("messages", msgs, "add_generation_prompt", true));

    assertThat(result)
      .contains("<|tool_call|>get_weather(")
      .contains("<|end_call|>")
      .contains("<|tool_response|>get_weather: 22°C sunny<|end_response|>")
      .endsWith("<|assistant|>\n");
  }

  @Test
  void tools_tojsonIndentForNonStringContent() {
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "test"),
      Map.<String, Object>of("role", "tool", "name", "fn", "content", Map.of("result", "ok"))
    );
    var result = env.getTemplate("synthetic-tools.jinja").render(Map.of("messages", msgs, "add_generation_prompt", false));

    // Non-string tool content should be rendered with tojson(indent=2)
    assertThat(result).contains("<|tool_response|>fn:");
    assertThat(result).contains("\"result\"");
  }

  // ===========================================================================
  // synthetic-thinking.jinja
  // ===========================================================================

  @Test
  void thinking_basicConversation() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    var result = tmpl.render(Map.of("messages", messageMaps(STANDARD), "add_generation_prompt", true));

    assertThat(result)
      .contains("<|turn|>system\nYou are a helpful assistant.\n<|end_turn|>")
      .contains("<|turn|>user\nWhat is 2+2?\n<|end_turn|>")
      .endsWith("<|turn|>model\n");
  }

  @Test
  void thinking_withThinkingEnabled() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    var result = tmpl.render(
      Map.of("messages", messageMaps(STANDARD), "add_generation_prompt", true, "enable_thinking", true)
    );

    assertThat(result).endsWith("<|turn|>model\n<|think|>\n");
  }

  @Test
  void thinking_embeddedThinkTags() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    // Assistant content with embedded <think>...</think> tags
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "Hi"),
      Map.of("role", "assistant", "content", "<think>\nLet me think\n</think>\nHello!")
    );
    var result = tmpl.render(Map.of("messages", msgs, "add_generation_prompt", false, "enable_thinking", true));

    assertThat(result).contains("<|think|>\nLet me think\n<|end_think|>").contains("Hello!\n<|end_turn|>");
  }

  @Test
  void thinking_reasoningViaGet() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    // Message with reasoning_content field (via .get() fallback chain)
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "Hi"),
      Map.of("role", "assistant", "content", "Hello!", "reasoning_content", "I should greet the user")
    );
    var result = tmpl.render(Map.of("messages", msgs, "add_generation_prompt", false, "enable_thinking", true));

    assertThat(result).contains("<|think|>\nI should greet the user\n<|end_think|>").contains("Hello!");
  }

  @Test
  void thinking_disabledThinking() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "Hi"),
      Map.of("role", "assistant", "content", "Hello!", "reasoning_content", "I should greet")
    );
    var result = tmpl.render(Map.of("messages", msgs, "add_generation_prompt", true, "enable_thinking", false));

    // Reasoning should NOT appear when thinking is disabled
    assertThat(result).doesNotContain("<|think|>");
    assertThat(result).contains("Hello!");
    // Generation prompt should NOT include think tag
    assertThat(result).endsWith("<|turn|>model\n");
  }

  @Test
  void thinking_toolResponseAsSequence() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    // Tool message with content as a list (exercises `is sequence and is not string`)
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "test"),
      Map.of("role", "tool", "content", List.of("result1", "result2"))
    );
    var result = tmpl.render(Map.of("messages", msgs, "add_generation_prompt", false));

    assertThat(result).contains("<|turn|>tool").contains("\"result1\", \"result2\"");
  }

  @Test
  void thinking_adjacentItemAccess() {
    var tmpl = env.getTemplate("synthetic-thinking.jinja");
    // Assistant followed by tool → should emit <|awaiting_tool|>
    var msgs = List.of(
      Map.<String, Object>of("role", "user", "content", "test"),
      Map.of("role", "assistant", "content", "calling tool"),
      Map.of("role", "tool", "content", "tool result")
    );
    var result = tmpl.render(Map.of("messages", msgs, "add_generation_prompt", false));

    assertThat(result).contains("<|awaiting_tool|>");
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private static Map<String, Object> ctx(List<Message> messages, boolean genPrompt) {
    var map = new LinkedHashMap<String, Object>();
    map.put("messages", messageMaps(messages));
    map.put("add_generation_prompt", genPrompt);
    map.put("bos_token", "<BOS>");
    map.put("eos_token", "</s>");
    return map;
  }

  private static List<Map<String, String>> messageMaps(List<Message> messages) {
    var result = new ArrayList<Map<String, String>>();
    for (var msg : messages) {
      var map = new LinkedHashMap<String, String>();
      map.put("role", msg.role());
      map.put("content", msg.content());
      result.add(map);
    }
    return result;
  }
}
