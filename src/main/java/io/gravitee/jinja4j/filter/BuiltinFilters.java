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
package io.gravitee.jinja4j.filter;

import io.gravitee.jinja4j.filter.builtin.*;

/**
 * Facade over the built-in filter registry.
 *
 * <p>Internally this class pre-populates a {@link FilterRegistry} with every
 * built-in filter from {@code io.gravitee.jinja4j.filter.builtin}. Use
 * {@link #registry()} to inspect or copy it.</p>
 */
public final class BuiltinFilters {

  private BuiltinFilters() {}

  private static final FilterRegistry REGISTRY = buildDefaults();

  /** Shared singleton registry of built-in filters. */
  public static FilterRegistry registry() {
    return REGISTRY;
  }

  /** Look up a built-in filter by name or alias, or {@code null} if absent. */
  public static FilterFunction get(String name) {
    return REGISTRY.get(name).orElse(null);
  }

  /** Whether a built-in filter with the given name or alias exists. */
  public static boolean has(String name) {
    return REGISTRY.has(name);
  }

  private static FilterRegistry buildDefaults() {
    return FilterRegistry.empty()
      .register(AbsFilter.INSTANCE)
      .register(CapitalizeFilter.INSTANCE)
      .register(DefaultFilter.INSTANCE)
      .register(DictsortFilter.INSTANCE)
      .register(FirstFilter.INSTANCE)
      .register(FloatFilter.INSTANCE)
      .register(IntFilter.INSTANCE)
      .register(JoinFilter.INSTANCE)
      .register(ItemsFilter.INSTANCE)
      .register(KeysFilter.INSTANCE)
      .register(LastFilter.INSTANCE)
      .register(LengthFilter.INSTANCE)
      .register(ListFilter.INSTANCE)
      .register(LowerFilter.INSTANCE)
      .register(MaxFilter.INSTANCE)
      .register(MinFilter.INSTANCE)
      .register(RegexFindallFilter.INSTANCE)
      .register(RegexFirstFilter.INSTANCE)
      .register(ReplaceFilter.INSTANCE)
      .register(SplitFilter.INSTANCE)
      .register(ReverseFilter.INSTANCE)
      .register(RoundFilter.INSTANCE)
      .register(SafeFilter.INSTANCE)
      .register(SelectFilter.INSTANCE)
      .register(SelectattrFilter.INSTANCE)
      .register(RejectFilter.INSTANCE)
      .register(RejectattrFilter.INSTANCE)
      .register(SortFilter.INSTANCE)
      .register(StringFilter.INSTANCE)
      .register(TitleFilter.INSTANCE)
      .register(TrimFilter.INSTANCE)
      .register(UniqueFilter.INSTANCE)
      .register(UpperFilter.INSTANCE)
      .register(UrlencodeFilter.INSTANCE)
      .register(ValuesFilter.INSTANCE)
      .register(MapFilter.INSTANCE)
      .register(BatchFilter.INSTANCE)
      .register(ToJsonFilter.INSTANCE)
      .register(IndentFilter.INSTANCE)
      .register(StriptagsFilter.INSTANCE)
      .register(WordcountFilter.INSTANCE)
      .register(TruncateFilter.INSTANCE)
      .register(AttrFilter.INSTANCE)
      .register(EscapeFilter.INSTANCE)
      .register(XmlattrFilter.INSTANCE)
      .register(GroupbyFilter.INSTANCE)
      .register(SumFilter.INSTANCE)
      .register(CenterFilter.INSTANCE)
      .register(FilesizeformatFilter.INSTANCE)
      .register(FormatFilter.INSTANCE);
  }
}
