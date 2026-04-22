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

import io.gravitee.jinja4j.chat.ChatTemplateRenderer;
import io.gravitee.jinja4j.chat.Message;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for chat templates.
 * Each test renders a representative chat template
 * and asserts the exact output matches the expected format.
 */
class ChatTemplateTest {

  private static final List<Message> STANDARD_MESSAGES = List.of(
    new Message("system", "You are a helpful assistant."),
    new Message("user", "What is the capital of France?")
  );

  private static final List<Message> MULTI_TURN = List.of(
    new Message("system", "You are a helpful assistant."),
    new Message("user", "Hi"),
    new Message("assistant", "Hello! How can I help?"),
    new Message("user", "What is 2+2?")
  );

  @Test
  void instructStyleTemplate() {
    var template = """
      {{- bos_token }}\
      {% for message in messages %}\
      {% if message['role'] == 'user' %}\
      {{- '[INST] ' + message['content'] + ' [/INST]' }}\
      {% elif message['role'] == 'assistant' %}\
      {{- message['content'] + eos_token }}\
      {% endif %}\
      {% endfor %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(
      new Message("user", "What is the capital of France?"),
      new Message("assistant", "Paris is the capital of France."),
      new Message("user", "And Germany?")
    );
    var result = renderer.render(messages, Map.of("bos_token", "<s>", "eos_token", "</s>"));

    var expected =
      "<s>[INST] What is the capital of France? [/INST]" +
      "Paris is the capital of France.</s>" +
      "[INST] And Germany? [/INST]";

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void imStartEndTemplate() {
    var template = """
      {% for message in messages %}\
      {%- if message['role'] == 'system' %}\
      {{- '<|im_start|>system\\n' + message['content'] + '<|im_end|>\\n' }}\
      {%- elif message['role'] == 'user' %}\
      {{- '<|im_start|>user\\n' + message['content'] + '<|im_end|>\\n' }}\
      {%- elif message['role'] == 'assistant' %}\
      {{- '<|im_start|>assistant\\n' + message['content'] + '<|im_end|>\\n' }}\
      {%- endif %}\
      {% endfor %}\
      {% if add_generation_prompt %}\
      {{- '<|im_start|>assistant\\n' }}\
      {% endif %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES, Map.of("add_generation_prompt", true));

    var expected =
      "<|im_start|>system\n" +
      "You are a helpful assistant.<|im_end|>\n" +
      "<|im_start|>user\n" +
      "What is the capital of France?<|im_end|>\n" +
      "<|im_start|>assistant\n";

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void imStartEndWithNoneCheckTemplate() {
    var template = """
      {%- for message in messages %}\
      {%- if message['role'] == 'system' %}\
      {{- '<|im_start|>system\\n' + message['content'] + '<|im_end|>\\n' }}\
      {%- elif message['role'] == 'user' %}\
      {{- '<|im_start|>user\\n' + message['content'] + '<|im_end|>\\n' }}\
      {%- elif message['role'] == 'assistant' %}\
      {%- if message['content'] is not none %}\
      {{- '<|im_start|>assistant\\n' + message['content'] + '<|im_end|>\\n' }}\
      {%- else %}\
      {{- '<|im_start|>assistant\\n' }}\
      {%- endif %}\
      {%- endif %}\
      {%- endfor %}\
      {%- if add_generation_prompt %}\
      {{- '<|im_start|>assistant\\n' }}\
      {%- endif %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES, Map.of("add_generation_prompt", true));

    var expected =
      "<|im_start|>system\n" +
      "You are a helpful assistant.<|im_end|>\n" +
      "<|im_start|>user\n" +
      "What is the capital of France?<|im_end|>\n" +
      "<|im_start|>assistant\n";

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void roleDynamicInsertionTemplate() {
    var template = """
      {% for message in messages %}\
      <|im_start|>{{ message['role'] }}
      {{ message['content'] }}<|im_end|>
      {% endfor %}\
      {% if add_generation_prompt %}\
      <|im_start|>assistant
      {% endif %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES, Map.of("add_generation_prompt", true));

    var expected =
      "<|im_start|>system\n" +
      "You are a helpful assistant.<|im_end|>\n" +
      "<|im_start|>user\n" +
      "What is the capital of France?<|im_end|>\n" +
      "<|im_start|>assistant\n";

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void loopLastWithAddGeneration() {
    var template = """
      {% for message in messages %}\
      {{ message['content'] }}\
      {% if not loop.last %}{{ eos_token }}{% endif %}\
      {% endfor %}\
      {% if add_generation_prompt %}GENERATE{% endif %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES, Map.of("add_generation_prompt", true, "eos_token", "<EOS>"));

    assertThat(result).isEqualTo("You are a helpful assistant.<EOS>What is the capital of France?GENERATE");
  }

  @Test
  void dictStyleKeyAccess() {
    var template = "{% for msg in messages %}{{ msg['role'] }}: {{ msg['content'] }}\n{% endfor %}";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES);

    assertThat(result).isEqualTo("system: You are a helpful assistant.\nuser: What is the capital of France?\n");
  }

  @Test
  void trimFilter() {
    var template = "{% for msg in messages %}{{ msg['content'] | trim }}{% endfor %}";
    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("user", "  hello  "));
    assertThat(renderer.render(messages)).isEqualTo("hello");
  }

  @Test
  void namespaceInChatTemplate() {
    var template = """
      {% set ns = namespace(found=false) %}\
      {% for message in messages %}\
      {% if message['role'] == 'system' %}{% set ns.found = true %}{% endif %}\
      {% endfor %}\
      {% if ns.found %}HAS_SYSTEM{% else %}NO_SYSTEM{% endif %}""";

    var renderer = ChatTemplateRenderer.of(template);
    assertThat(renderer.render(STANDARD_MESSAGES)).isEqualTo("HAS_SYSTEM");

    var noSystem = List.of(new Message("user", "Hi"));
    assertThat(renderer.render(noSystem)).isEqualTo("NO_SYSTEM");
  }

  @Test
  void raiseExceptionInTemplate() {
    var template = """
      {% for message in messages %}\
      {% if message['role'] == 'system' %}\
      {{ raise_exception('System messages not supported') }}\
      {% endif %}\
      {% endfor %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("system", "You are helpful."));

    assertThatThrownBy(() -> renderer.render(messages))
      .isInstanceOf(TemplateException.class)
      .hasMessageContaining("System messages not supported");
  }

  @Test
  void generationTagInTemplate() {
    var template = """
      {% for message in messages %}\
      {{ message['content'] }}\
      {% endfor %}\
      {% generation %}""";

    var renderer = ChatTemplateRenderer.of(template);
    assertThat(renderer.render(STANDARD_MESSAGES)).isEqualTo("You are a helpful assistant.What is the capital of France?");
  }

  @Test
  void bosTokenAtStart() {
    var template = "{{ bos_token }}{% for msg in messages %}{{ msg['content'] }}{% endfor %}";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(List.of(new Message("user", "Hello")), Map.of("bos_token", "<|begin_of_text|>"));
    assertThat(result).isEqualTo("<|begin_of_text|>Hello");
  }

  @Test
  void multiTurnConversation() {
    var template = """
      {{- bos_token }}\
      {% for message in messages %}\
      {{- '<|start|>' + message['role'] + '<|sep|>\\n' + message['content'] + '<|end|>' }}\
      {% endfor %}\
      {% if add_generation_prompt %}\
      {{- '<|start|>assistant<|sep|>\\n' }}\
      {% endif %}""";

    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(MULTI_TURN, Map.of("add_generation_prompt", true, "bos_token", "<BOS>"));

    var expected =
      "<BOS>" +
      "<|start|>system<|sep|>\nYou are a helpful assistant.<|end|>" +
      "<|start|>user<|sep|>\nHi<|end|>" +
      "<|start|>assistant<|sep|>\nHello! How can I help?<|end|>" +
      "<|start|>user<|sep|>\nWhat is 2+2?<|end|>" +
      "<|start|>assistant<|sep|>\n";

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void specialTokensNotEscaped() {
    var template = "{{ bos_token }}{{ eos_token }}";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(List.of(), Map.of("bos_token", "<|begin_of_text|>", "eos_token", "<|end_of_text|>"));
    assertThat(result).isEqualTo("<|begin_of_text|><|end_of_text|>");
  }

  // ---- Filters used within chat templates ----

  @Test
  void upperFilterInRoleRendering() {
    var template = """
      {% for msg in messages %}\
      [{{ msg['role'] | upper }}] {{ msg['content'] }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES);
    assertThat(result).contains("[SYSTEM] You are a helpful assistant.").contains("[USER] What is the capital of France?");
  }

  @Test
  void tojsonFilterInToolCallTemplate() {
    var template = """
      {% for msg in messages %}\
      {% if msg['role'] == 'assistant' and msg['tool_calls'] is defined %}\
      {% for tc in msg['tool_calls'] %}\
      <tool_call>{{ tc | tojson }}</tool_call>
      {% endfor %}\
      {% else %}\
      {{ msg['content'] }}
      {% endif %}\
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("user", "What is the weather?"));
    var result = renderer.render(messages);
    assertThat(result).contains("What is the weather?");
  }

  @Test
  void replaceFilterInContentNormalization() {
    var template = """
      {% for msg in messages %}\
      {{ msg['content'] | replace('\\r\\n', '\\n') | trim }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("user", "  hello\r\nworld  "));
    var result = renderer.render(messages);
    assertThat(result).contains("hello\nworld");
  }

  @Test
  void lengthFilterForConditionalFormatting() {
    var template = """
      {% if messages | length > 1 %}\
      MULTI_MSG\
      {% else %}\
      SINGLE_MSG\
      {% endif %}""";
    var renderer = ChatTemplateRenderer.of(template);
    assertThat(renderer.render(STANDARD_MESSAGES)).isEqualTo("MULTI_MSG");
    assertThat(renderer.render(List.of(new Message("user", "Hi")))).isEqualTo("SINGLE_MSG");
  }

  @Test
  void firstAndLastFiltersInChatContext() {
    var template = """
      FIRST={{ messages | first | attr('role') }} \
      LAST={{ messages | last | attr('role') }}""";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(STANDARD_MESSAGES);
    assertThat(result).isEqualTo("FIRST=system LAST=user");
  }

  @Test
  void selectattrAndMapInChatTemplate() {
    var template = """
      {% set user_msgs = messages | selectattr('role', 'equalto', 'user') %}\
      {% for msg in user_msgs %}\
      USER: {{ msg['content'] }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(MULTI_TURN);
    assertThat(result).contains("USER: Hi").contains("USER: What is 2+2?");
    assertThat(result).doesNotContain("system").doesNotContain("assistant");
  }

  @Test
  void joinFilterForToolList() {
    var template = """
      {% if tools is defined %}\
      Available tools: {{ tools | join(', ') }}
      {% endif %}\
      {% for msg in messages %}{{ msg['content'] }}{% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(
      List.of(new Message("user", "Hi")),
      Map.of("tools", List.of("search", "calculator", "browser"))
    );
    assertThat(result).contains("Available tools: search, calculator, browser").contains("Hi");
  }

  @Test
  void defaultFilterForMissingToken() {
    var template = """
      {{ bos_token | default('') }}\
      {% for msg in messages %}\
      {{ msg['content'] }}\
      {% endfor %}\
      {{ eos_token | default('') }}""";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(List.of(new Message("user", "Hello")));
    assertThat(result).isEqualTo("Hello");

    var resultWithTokens = renderer.render(
      List.of(new Message("user", "Hello")),
      Map.of("bos_token", "<s>", "eos_token", "</s>")
    );
    assertThat(resultWithTokens).isEqualTo("<s>Hello</s>");
  }

  @Test
  void rejectFilterToSkipSystemMessages() {
    var template = """
      {% for msg in messages | rejectattr('role', 'equalto', 'system') %}\
      {{ msg['role'] }}: {{ msg['content'] }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var result = renderer.render(MULTI_TURN);
    assertThat(result).doesNotContain("system");
    assertThat(result).contains("user: Hi").contains("assistant: Hello!").contains("user: What is 2+2?");
  }

  @Test
  void escapeFilterInChatTemplate() {
    var template = """
      {% for msg in messages %}\
      {{ msg['content'] | e }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("user", "What is <b>bold</b> & 'italic'?"));
    var result = renderer.render(messages);
    assertThat(result).contains("&lt;b&gt;bold&lt;/b&gt;").contains("&amp;").contains("&#39;italic&#39;");
  }

  @Test
  void wordcountFilterForContentAnalysis() {
    var template = """
      {% for msg in messages %}\
      [{{ msg['content'] | wordcount }} words] {{ msg['content'] }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("user", "What is the capital of France?"));
    var result = renderer.render(messages);
    assertThat(result).contains("[6 words]");
  }

  @Test
  void truncateFilterForContentPreview() {
    var template = """
      {% for msg in messages %}\
      {{ msg['role'] }}: {{ msg['content'] | truncate(15) }}
      {% endfor %}""";
    var renderer = ChatTemplateRenderer.of(template);
    var messages = List.of(new Message("user", "What is the capital of France and why is it important?"));
    var result = renderer.render(messages);
    assertThat(result).contains("user: What is the ...");
  }
}
