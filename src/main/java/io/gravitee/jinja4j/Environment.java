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
import io.gravitee.jinja4j.filter.BuiltinFilters;
import io.gravitee.jinja4j.filter.FilterFunction;
import io.gravitee.jinja4j.filter.FilterRegistry;
import io.gravitee.jinja4j.filter.NamedFilter;
import io.gravitee.jinja4j.loader.*;
import io.gravitee.jinja4j.test.BuiltinTests;
import io.gravitee.jinja4j.test.NamedTest;
import io.gravitee.jinja4j.test.TestFunction;
import io.gravitee.jinja4j.test.TestRegistry;
import io.gravitee.jinja4j.value.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main entry point for Jinja4j. An Environment holds configuration,
 * template loaders, custom filters/tests/globals, and a template cache.
 *
 * <pre>{@code
 * var env = new Environment();
 * env.addTemplate("hello.txt", "Hello {{ name }}!");
 * var tmpl = env.getTemplate("hello.txt");
 * String result = tmpl.render(Map.of("name", "World"));
 * // → "Hello World!"
 * }</pre>
 */
public final class Environment {

  private final MapLoader inlineTemplates = new MapLoader(new LinkedHashMap<>());
  private final List<TemplateLoader> loaders = new ArrayList<>();
  private final FilterRegistry customFilters = FilterRegistry.empty();
  private final TestRegistry customTests = TestRegistry.empty();
  private final Map<String, Value> globals = new LinkedHashMap<>();
  private final Map<String, Node.Template> templateCache = new ConcurrentHashMap<>();
  private boolean autoEscaping = false;
  private boolean undefinedBehaviorStrict = false;

  public Environment() {
    loaders.add(inlineTemplates);
    registerBuiltinGlobals();
  }

  // ---- Template management ----

  /**
   * Add a template from a string source.
   */
  public void addTemplate(String name, String source) {
    inlineTemplates.put(name, source);
    templateCache.remove(name);
  }

  /**
   * Add a template loader.
   */
  public void addLoader(TemplateLoader loader) {
    loaders.add(loader);
  }

  /**
   * Get a parsed template by name.
   */
  public Template getTemplate(String name) {
    var ast = templateCache.computeIfAbsent(name, this::parseTemplate);
    return new Template(name, ast, this);
  }

  /**
   * Parse an anonymous template from a string.
   */
  public Template fromString(String source) {
    return fromString(source, "<string>");
  }

  /**
   * Parse an anonymous template from a string with a name.
   */
  public Template fromString(String source, String name) {
    var lexer = new Lexer(source, name);
    var tokens = lexer.tokenizeAndTrim();
    var parser = new Parser(tokens, name);
    var ast = parser.parse();
    return new Template(name, ast, this);
  }

  // ---- Configuration ----

  public boolean isAutoEscaping() {
    return autoEscaping;
  }

  public void setAutoEscaping(boolean autoEscaping) {
    this.autoEscaping = autoEscaping;
  }

  public boolean isUndefinedBehaviorStrict() {
    return undefinedBehaviorStrict;
  }

  public void setUndefinedBehaviorStrict(boolean strict) {
    this.undefinedBehaviorStrict = strict;
  }

  // ---- Extensibility ----

  /**
   * Register a named custom filter. Custom entries take precedence over built-ins
   * of the same name.
   */
  public void addFilter(NamedFilter filter) {
    customFilters.register(filter);
  }

  /**
   * Register a named custom test. Custom entries take precedence over built-ins
   * of the same name.
   */
  public void addTest(NamedTest test) {
    customTests.register(test);
  }

  /**
   * @deprecated Prefer {@link #addFilter(NamedFilter)} or {@link #filters()}
   *     to configure a custom filter as a class instance. This overload remains
   *     for backward compatibility.
   */
  @Deprecated
  public void addFilter(String name, FilterFunction filter) {
    customFilters.register(name, filter);
  }

  /**
   * @deprecated Prefer {@link #addTest(NamedTest)} or {@link #tests()} to
   *     configure a custom test as a class instance. This overload remains
   *     for backward compatibility.
   */
  @Deprecated
  public void addTest(String name, TestFunction test) {
    customTests.register(name, test);
  }

  /** Mutable custom filter registry. Custom entries shadow built-ins. */
  public FilterRegistry filters() {
    return customFilters;
  }

  /** Mutable custom test registry. Custom entries shadow built-ins. */
  public TestRegistry tests() {
    return customTests;
  }

  /**
   * Register a global variable.
   */
  public void addGlobal(String name, Object value) {
    globals.put(name, ValueConverter.convert(value));
  }

  /**
   * Register a callable global function.
   */
  public void addFunction(String name, TemplateFunction fn) {
    globals.put(name, Value.ofCallable(name, fn));
  }

  // ---- Internal: filter/test application ----

  Value applyFilter(String name, Value input, List<Value> args, Map<String, Value> kwargs, SourceLocation loc) {
    // Check custom filters first
    var custom = customFilters.get(name);
    if (custom.isPresent()) return custom.get().apply(input, args, kwargs, loc);

    var builtin = BuiltinFilters.get(name);
    if (builtin != null) return builtin.apply(input, args, kwargs, loc);

    throw new TemplateException("Unknown filter '%s'".formatted(name), loc);
  }

  boolean applyTest(String name, Value value, List<Value> args, SourceLocation loc) {
    var custom = customTests.get(name);
    if (custom.isPresent()) return custom.get().test(value, args, loc);

    var builtin = BuiltinTests.get(name);
    if (builtin != null) return builtin.test(value, args, loc);

    throw new TemplateException("Unknown test '%s'".formatted(name), loc);
  }

  // ---- Internal: template loading ----

  String loadTemplate(String name) {
    for (var loader : loaders) {
      var source = loader.load(name);
      if (source != null) return source;
    }
    return null;
  }

  Map<String, Value> getGlobals() {
    return Collections.unmodifiableMap(globals);
  }

  private Node.Template parseTemplate(String name) {
    var source = loadTemplate(name);
    if (source == null) {
      throw new TemplateException("Template '%s' not found".formatted(name), SourceLocation.UNKNOWN);
    }
    var lexer = new Lexer(source, name);
    var tokens = lexer.tokenizeAndTrim();
    var parser = new Parser(tokens, name);
    return parser.parse();
  }

  private void registerBuiltinGlobals() {
    // range(start, stop, step)
    globals.put(
      "range",
      Value.ofCallable("range", (args, kwargs) -> {
        long start = 0,
          stop,
          step = 1;
        if (args.size() == 1) {
          stop = args.getFirst().asLong();
        } else if (args.size() >= 2) {
          start = args.get(0).asLong();
          stop = args.get(1).asLong();
          if (args.size() >= 3) step = args.get(2).asLong();
        } else {
          return Value.ofList(List.of());
        }
        var list = new ArrayList<Value>();
        if (step > 0) {
          for (long i = start; i < stop; i += step) list.add(Value.of(i));
        } else if (step < 0) {
          for (long i = start; i > stop; i += step) list.add(Value.of(i));
        }
        return Value.ofList(list);
      })
    );

    // namespace(**kwargs) — creates a mutable namespace
    globals.put(
      "namespace",
      Value.ofCallable("namespace", (args, kwargs) -> {
        var ns = new Namespace(kwargs);
        return new Value.NamespaceVal(ns);
      })
    );

    // dict(**kwargs) — creates a dict from keyword args
    globals.put("dict", Value.ofCallable("dict", (args, kwargs) -> Value.ofMap(new LinkedHashMap<>(kwargs))));

    // raise_exception(msg)
    globals.put(
      "raise_exception",
      Value.ofCallable("raise_exception", (args, kwargs) -> {
        var msg = !args.isEmpty() ? args.getFirst().asString() : "Template error";
        throw new TemplateException(msg, SourceLocation.UNKNOWN);
      })
    );

    // joiner(sep)
    globals.put(
      "joiner",
      Value.ofCallable("joiner", (args, kwargs) -> {
        var sep = !args.isEmpty() ? args.getFirst().asString() : ", ";
        var first = new boolean[] { true };
        return Value.ofCallable("joiner_instance", (callArgs, callKwargs) -> {
          if (first[0]) {
            first[0] = false;
            return Value.of("");
          }
          return Value.of(sep);
        });
      })
    );

    // cycler(items...)
    globals.put(
      "cycler",
      Value.ofCallable("cycler", (args, kwargs) -> {
        var items = new ArrayList<>(args);
        var idx = new int[] { 0 };
        var map = new LinkedHashMap<String, Value>();
        map.put(
          "next",
          Value.ofCallable("cycler.next", (callArgs, callKwargs) -> {
            var item = items.get(idx[0] % items.size());
            idx[0]++;
            return item;
          })
        );
        map.put("current", items.isEmpty() ? Value.UNDEFINED : items.getFirst());
        return Value.ofMap(map);
      })
    );

    // lipsum(n, html, min, max) — generates lorem ipsum (simplified)
    globals.put(
      "lipsum",
      Value.ofCallable("lipsum", (args, kwargs) -> Value.of("Lorem ipsum dolor sit amet, consectetur adipiscing elit."))
    );

    // strftime_now(format) — renders the current date/time using Python strftime format
    globals.put(
      "strftime_now",
      Value.ofCallable("strftime_now", (args, kwargs) -> {
        var format = !args.isEmpty() ? args.getFirst().asString() : "%Y-%m-%d";
        var now = java.time.LocalDateTime.now();
        var javaFormat = pythonToJavaDateFormat(format);
        try {
          return Value.of(now.format(java.time.format.DateTimeFormatter.ofPattern(javaFormat, java.util.Locale.ENGLISH)));
        } catch (Exception e) {
          return Value.of(now.toString());
        }
      })
    );
  }

  /**
   * Translate a Python-style strftime format string into a Java
   * {@link java.time.format.DateTimeFormatter} pattern.
   *
   * <p>Performs a single left-to-right pass, matching each {@code %}-sequence
   * at its exact position. Literal text between directives is quoted with
   * single quotes so pattern letters (e.g. {@code e}) in surrounding prose
   * don't leak into the pattern grammar. Unknown directives are preserved
   * verbatim.</p>
   */
  static String pythonToJavaDateFormat(String format) {
    var sb = new StringBuilder(format.length());
    var literal = new StringBuilder();
    int i = 0;
    int n = format.length();
    while (i < n) {
      char c = format.charAt(i);
      if (c == '%' && i + 1 < n) {
        // %% is a literal percent — treat it as plain text so it gets quoted
        // together with any surrounding literal characters.
        if (format.charAt(i + 1) == '%') {
          literal.append('%');
          i += 2;
          continue;
        }
        // Look for a two- or three-char directive (the latter for %-d / %-m).
        String directive = null;
        String replacement = null;
        if (format.charAt(i + 1) == '-' && i + 2 < n) {
          directive = format.substring(i, i + 3);
          replacement = PY_DATE_DIRECTIVES.get(directive);
        }
        if (replacement == null) {
          directive = format.substring(i, i + 2);
          replacement = PY_DATE_DIRECTIVES.get(directive);
        }
        if (replacement != null) {
          if (literal.length() > 0) {
            appendLiteral(sb, literal);
            literal.setLength(0);
          }
          sb.append(replacement);
          i += directive.length();
          continue;
        }
      }
      literal.append(c);
      i++;
    }
    if (literal.length() > 0) {
      appendLiteral(sb, literal);
    }
    return sb.toString();
  }

  private static void appendLiteral(StringBuilder sb, CharSequence literal) {
    sb.append('\'');
    for (int k = 0; k < literal.length(); k++) {
      char c = literal.charAt(k);
      if (c == '\'') sb.append("''");
      else sb.append(c);
    }
    sb.append('\'');
  }

  private static final Map<String, String> PY_DATE_DIRECTIVES = buildPyDateDirectives();

  private static Map<String, String> buildPyDateDirectives() {
    // Insertion order does not matter — we look up by exact directive string.
    var map = new LinkedHashMap<String, String>();
    // Non-padded numeric variants first — they take priority at the call site.
    map.put("%-d", "d");
    map.put("%-m", "M");
    // Year / month / day.
    map.put("%Y", "yyyy");
    map.put("%y", "yy");
    map.put("%m", "MM");
    map.put("%d", "dd");
    map.put("%j", "DDD");
    // Hours, minutes, seconds.
    map.put("%H", "HH");
    map.put("%I", "hh");
    map.put("%M", "mm");
    map.put("%S", "ss");
    map.put("%p", "a");
    // Textual.
    map.put("%B", "MMMM");
    map.put("%b", "MMM");
    map.put("%A", "EEEE");
    map.put("%a", "EEE");
    // Composite.
    map.put("%c", "EEE MMM dd HH:mm:ss yyyy");
    map.put("%x", "MM/dd/yy");
    map.put("%X", "HH:mm:ss");
    return Collections.unmodifiableMap(map);
  }
}
