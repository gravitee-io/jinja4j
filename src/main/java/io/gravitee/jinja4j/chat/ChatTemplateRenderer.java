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
package io.gravitee.jinja4j.chat;

import io.gravitee.jinja4j.Environment;
import io.gravitee.jinja4j.Template;
import java.util.*;

/**
 * Convenience API for rendering chat templates.
 *
 * <p>Supports both single-string templates and the multi-template
 * array format where {@code chat_template} is a JSON array of
 * {@code {"name": "...", "template": "..."}} objects.</p>
 *
 * <pre>{@code
 * var renderer = ChatTemplateRenderer.of(chatTemplateString);
 * var messages = List.of(
 *     new Message("system", "You are a helpful assistant."),
 *     new Message("user", "What is 2+2?")
 * );
 * String prompt = renderer.render(messages, Map.of(
 *     "add_generation_prompt", true,
 *     "bos_token", "<|begin_of_text|>",
 *     "eos_token", "<|eot_id|>"
 * ));
 * }</pre>
 */
public final class ChatTemplateRenderer {

  private final Template template;

  private ChatTemplateRenderer(Template template) {
    this.template = template;
  }

  /**
   * Create a renderer from a raw Jinja2 chat template string.
   */
  public static ChatTemplateRenderer of(String templateSource) {
    var env = new Environment();
    // Auto-escaping must be OFF for LLM templates
    env.setAutoEscaping(false);
    return of(templateSource, env);
  }

  /**
   * Create a renderer using a pre-configured Environment.
   */
  public static ChatTemplateRenderer of(String templateSource, Environment env) {
    var tmpl = env.fromString(templateSource, "<chat_template>");
    return new ChatTemplateRenderer(tmpl);
  }

  /**
   * Create renderers from a multi-template array format.
   *
   * <p>Some models provide multiple named templates:</p>
   * <pre>{@code
   * [{"name": "default", "template": "..."}, {"name": "tool_use", "template": "..."}]
   * }</pre>
   *
   * @param templates list of named templates as maps with "name" and "template" keys
   * @return map of template name to renderer
   */
  public static Map<String, ChatTemplateRenderer> ofMultiple(List<Map<String, String>> templates) {
    var result = new LinkedHashMap<String, ChatTemplateRenderer>();
    for (var entry : templates) {
      var name = entry.get("name");
      var source = entry.get("template");
      if (name != null && source != null) {
        result.put(name, of(source));
      }
    }
    return result;
  }

  /**
   * Create a renderer by auto-detecting the format: either a plain Jinja2 string
   * or a JSON array of named templates. If an array is detected, returns the
   * "default" template or the first one if no "default" exists.
   *
   * @param rawField the raw chat_template field value from tokenizer_config.json
   * @return a renderer for the primary template
   */
  public static ChatTemplateRenderer fromRawField(String rawField) {
    var trimmed = rawField.strip();
    if (trimmed.startsWith("[")) {
      var templates = parseTemplateArray(trimmed);
      for (var entry : templates) {
        if ("default".equals(entry.get("name"))) {
          return of(entry.get("template"));
        }
      }
      if (!templates.isEmpty()) {
        return of(templates.getFirst().get("template"));
      }
      throw new IllegalArgumentException("Empty template array");
    }
    return of(rawField);
  }

  /**
   * Like {@link #fromRawField(String)} but returns a named template.
   *
   * @param rawField the raw chat_template field value
   * @param name the template name to select (e.g. "tool_use")
   * @return a renderer for the named template
   */
  public static ChatTemplateRenderer fromRawField(String rawField, String name) {
    var trimmed = rawField.strip();
    if (trimmed.startsWith("[")) {
      var templates = parseTemplateArray(trimmed);
      for (var entry : templates) {
        if (name.equals(entry.get("name"))) {
          return of(entry.get("template"));
        }
      }
      throw new IllegalArgumentException("Template '%s' not found in array".formatted(name));
    }
    return of(rawField);
  }

  /**
   * Render the chat template with the given messages and extra variables.
   */
  public String render(List<Message> messages, Map<String, Object> extraVars) {
    var ctx = new LinkedHashMap<String, Object>();

    // Convert messages to List<Map<String, String>>
    var messagesList = new ArrayList<Map<String, String>>();
    for (var msg : messages) {
      var msgMap = new LinkedHashMap<String, String>();
      msgMap.put("role", msg.role());
      msgMap.put("content", msg.content());
      messagesList.add(msgMap);
    }
    ctx.put("messages", messagesList);

    // Defaults
    ctx.put("add_generation_prompt", false);
    ctx.put("bos_token", "");
    ctx.put("eos_token", "");
    ctx.put("unk_token", "");
    ctx.put("pad_token", "");

    // Override with user-provided vars
    ctx.putAll(extraVars);

    return template.render(ctx);
  }

  /**
   * Render with default extra vars.
   */
  public String render(List<Message> messages) {
    return render(messages, Map.of());
  }

  // ---- Minimal JSON array parser for the multi-template format ----

  /**
   * Parses the multi-template array format.
   * Minimal parser — handles the specific shape:
   * {@code [{"name": "...", "template": "..."}, ...]}
   */
  static List<Map<String, String>> parseTemplateArray(String json) {
    var result = new ArrayList<Map<String, String>>();
    int i = 0;
    int len = json.length();

    // Skip to first '['
    while (i < len && json.charAt(i) != '[') i++;
    i++; // skip '['

    while (i < len) {
      // Skip whitespace and commas
      while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
      if (i >= len || json.charAt(i) == ']') break;

      if (json.charAt(i) == '{') {
        i++; // skip '{'
        var map = new LinkedHashMap<String, String>();
        while (i < len && json.charAt(i) != '}') {
          // Skip whitespace and commas
          while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
          if (i >= len || json.charAt(i) == '}') break;

          // Parse key
          var key = parseJsonString(json, i);
          i = key.endIndex();
          // Skip colon
          while (i < len && json.charAt(i) != ':') i++;
          i++; // skip ':'
          // Skip whitespace
          while (i < len && Character.isWhitespace(json.charAt(i))) i++;
          // Parse value
          var value = parseJsonString(json, i);
          i = value.endIndex();

          map.put(key.value(), value.value());
        }
        if (i < len) i++; // skip '}'
        result.add(map);
      } else {
        i++;
      }
    }
    return result;
  }

  private record ParsedString(String value, int endIndex) {}

  private static ParsedString parseJsonString(String json, int start) {
    int i = start;
    while (i < json.length() && json.charAt(i) != '"') i++;
    i++; // skip opening quote
    var sb = new StringBuilder();
    while (i < json.length() && json.charAt(i) != '"') {
      if (json.charAt(i) == '\\' && i + 1 < json.length()) {
        i++;
        var esc = json.charAt(i);
        switch (esc) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            // Four hex digits after backslash-u; must honour UTF-16 surrogate pairs.
            if (i + 4 >= json.length()) {
              throw new IllegalArgumentException("Truncated unicode escape in JSON string");
            }
            var hex = json.substring(i + 1, i + 5);
            if (!isHex4(hex)) {
              throw new IllegalArgumentException("Invalid unicode escape in JSON string: \\u" + hex);
            }
            var code = Integer.parseInt(hex, 16);
            if (
              Character.isHighSurrogate((char) code) &&
              i + 10 < json.length() &&
              json.charAt(i + 5) == '\\' &&
              json.charAt(i + 6) == 'u'
            ) {
              var lowHex = json.substring(i + 7, i + 11);
              if (isHex4(lowHex)) {
                var lowCode = Integer.parseInt(lowHex, 16);
                if (Character.isLowSurrogate((char) lowCode)) {
                  sb.appendCodePoint(Character.toCodePoint((char) code, (char) lowCode));
                  i += 10;
                  break;
                }
              }
            }
            sb.append((char) code);
            i += 4;
          }
          default -> {
            // Unknown escape: preserve both the backslash and the character.
            sb.append('\\').append(esc);
          }
        }
      } else {
        sb.append(json.charAt(i));
      }
      i++;
    }
    if (i < json.length()) i++; // skip closing quote
    return new ParsedString(sb.toString(), i);
  }

  private static boolean isHex4(String s) {
    if (s.length() != 4) return false;
    for (int k = 0; k < 4; k++) {
      char c = s.charAt(k);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) return false;
    }
    return true;
  }
}
