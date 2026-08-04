# Jinja4j

[![CircleCI](https://circleci.com/gh/gravitee-io/jinja4j.svg?style=svg)](https://circleci.com/gh/gravitee-io/jinja4j)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/jinja4j/blob/main/LICENSE)
[![Community Forum](https://img.shields.io/badge/Gravitee.io-Community%20Forum-blue?logo=discourse)](https://community.gravitee.io)

A lightweight, zero-dependency Jinja2-compatible template engine for Java,
inspired by [MiniJinja](https://github.com/mitsuhiko/minijinja).

## Quick Start

```java
var env = new Environment();
env.addTemplate("hello.txt", "Hello {{ name }}!");
var tmpl = env.getTemplate("hello.txt");
String result = tmpl.render(Map.of("name", "World"));
// -> "Hello World!"
```

### Parse from a string

```java
var env = new Environment();
var tmpl = env.fromString("{{ greeting }}, {{ name }}!");
String result = tmpl.render(Map.of("greeting", "Hi", "name", "Alice"));
// -> "Hi, Alice!"
```

### Fluent context builder

```java
var result = tmpl.render(Context.of("name", "World").and("count", 42));
```

## LLM Chat Template Rendering

The primary use case. Load a `chat_template` string from a HuggingFace
`tokenizer_config.json` and render it:

```java
String chatTemplate = // from tokenizer_config.json "chat_template" field
var renderer = ChatTemplateRenderer.of(chatTemplate);

var messages = List.of(
    new Message("system", "You are a helpful assistant."),
    new Message("user", "What is the capital of France?")
);

String prompt = renderer.render(messages, Map.of(
    "add_generation_prompt", true,
    "bos_token", "<|begin_of_text|>",
    "eos_token", "<|eot_id|>"
));
```

### Multi-template array format

Some models provide multiple named templates:

```java
// Auto-detect format and select "default" template
var renderer = ChatTemplateRenderer.fromRawField(rawJsonField);

// Select a specific named template
var toolRenderer = ChatTemplateRenderer.fromRawField(rawJsonField, "tool_use");
```

## Template Syntax

### Variables

```
{{ variable }}
{{ object.attribute }}
{{ map["key"] }}
{{ list[0] }}
```

### Filters

```
{{ name | upper }}
{{ items | join(", ") }}
{{ value | default("fallback") }}
{{ data | tojson(indent=4) }}
{{ tools | reject('equalto', 'hidden') | join(", ") }}
```

45+ built-in filters including: `abs`, `capitalize`, `default`, `dictsort`,
`e`/`escape`, `first`, `float`, `format`, `int`, `join`, `items`, `keys`, `last`,
`length`, `list`, `lower`, `map`, `max`, `min`, `reject`, `rejectattr`,
`replace`, `reverse`, `round`, `safe`, `select`, `selectattr`, `sort`,
`string`, `title`, `tojson`, `trim`, `truncate`, `unique`, `upper`,
`urlencode`, `values`, and more.

`indent` accepts `width`, `first`, and `blank` keyword arguments; `sort`
accepts a multi-key `attribute` (e.g. `attribute="last,first"`, dotted paths
supported) and is stable under `reverse=true`. `format` applies printf-style
formatting (`"%s has %d" | format(name, count)`).

### Control Flow

```
{% if condition %}...{% elif other %}...{% else %}...{% endif %}

{% for item in list %}
  {{ loop.index }} {{ item }}
{% else %}
  No items.
{% endfor %}
```

Loop variables: `loop.index`, `loop.index0`, `loop.first`, `loop.last`,
`loop.length`, `loop.revindex`, `loop.revindex0`, `loop.previtem`,
`loop.nextitem`, `loop.cycle(...)`.

### Expressions

```
{{ 1 + 2 }}              {# arithmetic: + - * / // % **  #}
{{ a == b }}              {# comparison: == != < > <= >=  #}
{{ 0 <= x <= 10 }}        {# chained comparisons          #}
{{ rows.0.1 }}            {# dotted integer lookup        #}
{{ a and b }}             {# logic: and or not            #}
{{ "x" in list }}         {# membership: in, not in      #}
{{ "yes" if ok else "no" }} {# ternary                   #}
{{ a ~ b }}               {# string concatenation         #}
```

### Tests

```
{{ x is defined }}
{{ x is not none }}
{{ n is odd }}
{{ n is divisibleby(3) }}
{{ items is sequence }}
{{ data is mapping }}
```

Built-in tests: `defined`, `undefined`, `none`, `boolean`, `integer`, `float`,
`number`, `string`, `sequence`, `mapping`, `iterable`, `callable`, `odd`,
`even`, `divisibleby`, `equalto`/`eq`, `ne`, `lt`/`lessthan`,
`gt`/`greaterthan`, `le`, `ge`, `sameas`, `in`, `lower`, `upper`, `true`,
`false`, `escaped`.

### Assignment and Scoping

```
{% set x = 42 %}
{% set a, b = pair %}                {# tuple unpacking #}
{% set ns = namespace(count=0) %}
{% set ns.count = ns.count + 1 %}
{% with x = 1, y = 2 %}...{% endwith %}
{% do ns.items.append(x) %}          {# evaluate, emit nothing #}
```

### Template Reuse

```
{% include "header.html" %}
{% extends "base.html" %}{% block content %}...{% endblock %}
{% block body required %}{% endblock %}   {# must be overridden #}
{% macro greet(name) %}Hello {{ name }}!{% endmacro %}
{{ greet("World") }}
{% import "macros.html" as m %}{{ m.greet("World") }}
{% from "macros.html" import greet as g %}{{ g("World") }}
```

### String Methods

Python-like string methods are available on string values:

```
{{ text.startswith("<tool>") }}
{{ text.split("::")[0].strip() }}
{{ role.title() }}
{{ content.replace("\r\n", "\n") }}
```

Supported: `startswith`, `endswith`, `split`, `strip`, `lstrip`, `rstrip`,
`upper`, `lower`, `title`, `replace`, `find`, `count`, `join`, `format`.

### Dict Methods

```
{{ message.get('reasoning', 'none') }}
{{ data.keys() }}
{{ data.items() }}
```

### Built-in Globals

```
{{ range(10) }}
{{ namespace(count=0) }}
{{ dict(a=1, b=2) }}
{{ raise_exception("error message") }}
{{ strftime_now("%B %d, %Y") }}
```

### Whitespace Control

```
{%- trim left -%}
{{- trim expression -}}
{#- trim comment -#}
```

Environment-wide whitespace policies and custom delimiters are also
configurable:

```java
env.setTrimBlocks(true);          // drop the first newline after a block tag
env.setLstripBlocks(true);        // strip leading inline whitespace before block tags
env.setKeepTrailingNewline(false);// strip a single trailing template newline
env.setDelimiters("<%", "%>", "<<", ">>", "<#", "#>"); // custom delimiters
```

### Special Tags

```
{% generation %}   {# MiniJinja marker -- treated as no-op #}
{% raw %}...{% endraw %}
{% autoescape true %}{{ html }}{% endautoescape %}   {# scoped auto-escaping #}
```

## Template Loaders

```java
// In-memory
env.addTemplate("name", "source");

// Classpath resources
env.addLoader(new ClasspathLoader("templates"));

// Filesystem
env.addLoader(new FileSystemLoader("/path/to/templates"));
```

## Extensibility

Jinja4j exposes two complementary APIs for registering custom filters and
tests:

- **Class-based** (`NamedFilter` / `NamedTest`) — the recommended, self-describing form, with built-in support for aliases.
- **Lambda** (`addFilter(String, FilterFunction)` / `addTest(String, TestFunction)`) — kept for simple, one-off customizations.

Custom entries **always take precedence** over the built-ins of the same
name, so you can override any built-in by re-registering it.

### Custom Filters

#### Lambda form (quick one-liner)

```java
var env = new Environment();
env.addFilter("double", (v, args, kwargs, loc) ->
    Value.of(v.asString() + v.asString()));

env.fromString("{{ 'ab' | double }}").render();
// -> "abab"
```

#### Class form with aliases (recommended)

Implement `NamedFilter`. Expose a stateless singleton via an `INSTANCE`
field. Declare aliases via `aliases()`. The signature of `apply` gives
you access to positional args, keyword args and the source location for
precise error reporting.

```java
package com.example.filter;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.TemplateException;
import io.gravitee.jinja4j.filter.NamedFilter;
import io.gravitee.jinja4j.value.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns "Hello World!" into "hello-world". */
public final class SlugifyFilter implements NamedFilter {

    public static final SlugifyFilter INSTANCE = new SlugifyFilter();

    private SlugifyFilter() {}

    @Override
    public String name() {
        return "slugify";
    }

    @Override
    public List<String> aliases() {
        return List.of("slug");
    }

    @Override
    public Value apply(Value input, List<Value> args,
                       Map<String, Value> kwargs, SourceLocation loc) {
        if (!input.isString()) {
            throw new TemplateException("slugify expects a string", loc);
        }
        var separator = args.isEmpty() ? "-" : args.getFirst().asString();
        var normalized = input.asString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", separator)
                .replaceAll("^" + separator + "+|" + separator + "+$", "");
        return Value.of(normalized);
    }
}
```

Register the singleton and use it:

```java
var env = new Environment();
env.addFilter(SlugifyFilter.INSTANCE);

env.fromString("{{ 'Hello, World!' | slugify }}").render();
// -> "hello-world"

env.fromString("{{ 'Foo bar baz' | slug('_') }}").render();
// -> "foo_bar_baz"
```

### Custom Tests

`NamedTest` mirrors `NamedFilter` — it carries `name()`, optional
`aliases()`, and a `test(value, args, loc)` method returning a `boolean`:

```java
package com.example.test;

import io.gravitee.jinja4j.SourceLocation;
import io.gravitee.jinja4j.test.NamedTest;
import io.gravitee.jinja4j.value.Value;

import java.util.List;

/** Reports whether the subject reads the same forwards and backwards. */
public final class PalindromeTest implements NamedTest {

    public static final PalindromeTest INSTANCE = new PalindromeTest();

    private PalindromeTest() {}

    @Override
    public String name() {
        return "palindrome";
    }

    @Override
    public boolean test(Value value, List<Value> args, SourceLocation loc) {
        if (!value.isString()) return false;
        var s = value.asString().toLowerCase();
        return new StringBuilder(s).reverse().toString().equals(s);
    }
}
```

```java
var env = new Environment();
env.addTest(PalindromeTest.INSTANCE);

env.fromString("{{ 'racecar' is palindrome }}").render();
// -> "True"

env.fromString("{{ words | select('palindrome') | join(',') }}")
   .render(Map.of("words", List.of("hello", "level", "kayak", "world")));
// -> "level,kayak"
```

### Overriding a built-in

Because custom registrations have priority, you can replace a built-in in
place. For example, swap the default `upper` filter for a "shouting" variant:

```java
env.addFilter(new NamedFilter() {
    @Override public String name() { return "upper"; }
    @Override public Value apply(Value v, List<Value> args,
                                 Map<String, Value> kwargs, SourceLocation loc) {
        return Value.of(v.asString().toUpperCase() + "!!!");
    }
});

env.fromString("{{ 'hi' | upper }}").render();
// -> "HI!!!"
```

### Managing the registry directly

Every `Environment` owns a mutable `FilterRegistry` and `TestRegistry`.
Use them for bulk registration, removal, or inspection:

```java
env.filters().register(SlugifyFilter.INSTANCE);
env.filters().register("myfilter", (v, args, kw, loc) -> Value.of("ok"));
env.filters().unregister("myfilter");

boolean has = env.filters().has("slugify"); // true
```

### Custom Globals

```java
env.addGlobal("app_name", "MyApp");
env.addFunction("add", (args, kwargs) ->
    Value.of(args.get(0).asLong() + args.get(1).asLong()));
```

## Auto-Escaping

Auto-escaping is **off by default** -- this is critical for LLM use, where
tokens like `<|begin_of_text|>` must pass through literally.

```java
// Enable for HTML templates
env.setAutoEscaping(true);

// Mark a value as pre-escaped
{{ trusted_html | safe }}
```

## Error Handling

All errors throw `TemplateException` (unchecked) with location info:

```java
try {
    tmpl.render(ctx);
} catch (TemplateException e) {
    e.getTemplateName(); // "index.html"
    e.getLine();         // 5
    e.getColumn();       // 12
    e.getDetail();       // "undefined variable 'user'"
}
```

## Tested With Synthetic Templates

The engine is tested against **3 synthetic chat templates** that collectively exercise
every Jinja2 idiom found in LLM chat templates:

| Template | Features Exercised |
|----------|--------------------|
| `synthetic-simple.jinja` | Role dispatch, system extraction, `messages[1:]` slicing, `.strip().replace()` chains, `raise_exception()`, `loop.first`/`loop.last`, `loop.index0 % 2`, `.title()`, `is defined` guard |
| `synthetic-tools.jinja` | Recursive macros, `namespace()`, `for key, value in dict.items()`, `tojson(indent=2)`, `\| join`, `\| trim`, `\| length`, `in`/`not in`, `is string`/`is mapping`/`is none`/`is iterable`, inline conditional, `strftime_now()`, block comments, `range()` |
| `synthetic-thinking.jinja` | `namespace()` mutation, `.split()` + negative indexing, `.startswith()`/`.endswith()`, `.lstrip()`/`.rstrip()`, `.get()` with fallback, `is false`, `is sequence`, `messages[loop.index0 + 1]` adjacent access, `messages\|length - 1` arithmetic |

Additional granular idiom coverage is provided by `TemplateIdiomTest` (47 synthetic tests).

## Project Structure

```
src/main/java/io/gravitee/jinja4j/
  Environment.java          # Main entry point
  Template.java             # Parsed template
  Context.java              # Fluent context builder
  Lexer.java                # Tokenizer
  Parser.java               # Recursive-descent + Pratt parser
  Interpreter.java          # AST evaluator
  RenderContext.java         # Variable scoping
  Token.java                # Sealed token types
  TemplateException.java    # Error type with location
  SourceLocation.java       # Line/column tracking
  ast/Node.java             # Sealed AST node types
  value/Value.java          # Sealed value type hierarchy
  value/ValueConverter.java # Java -> Value conversion
  value/Namespace.java      # Mutable namespace
  value/SafeString.java     # Pre-escaped string
  value/TemplateFunction.java
  filter/BuiltinFilters.java
  test/BuiltinTests.java
  loader/TemplateLoader.java
  loader/MapLoader.java
  loader/ClasspathLoader.java
  loader/FileSystemLoader.java
  chat/ChatTemplateRenderer.java
  chat/Message.java
```

## Requirements

- Java 25+ (uses sealed interfaces, records, pattern matching)
- No runtime dependencies

## License

[Apache License 2.0](LICENSE)