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
package io.gravitee.jinja4j.filter.builtin;

import static org.assertj.core.api.Assertions.*;

import io.gravitee.jinja4j.filter.NamedFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Smoke tests verifying every builtin filter class is a singleton with the expected name/aliases. */
class BuiltinFilterSmokeTest {

  @Test
  void namesMatchExpected() {
    assertThat(AbsFilter.INSTANCE.name()).isEqualTo("abs");
    assertThat(CapitalizeFilter.INSTANCE.name()).isEqualTo("capitalize");
    assertThat(DefaultFilter.INSTANCE.name()).isEqualTo("default");
    assertThat(DictsortFilter.INSTANCE.name()).isEqualTo("dictsort");
    assertThat(FirstFilter.INSTANCE.name()).isEqualTo("first");
    assertThat(FloatFilter.INSTANCE.name()).isEqualTo("float");
    assertThat(IntFilter.INSTANCE.name()).isEqualTo("int");
    assertThat(JoinFilter.INSTANCE.name()).isEqualTo("join");
    assertThat(ItemsFilter.INSTANCE.name()).isEqualTo("items");
    assertThat(KeysFilter.INSTANCE.name()).isEqualTo("keys");
    assertThat(LastFilter.INSTANCE.name()).isEqualTo("last");
    assertThat(LengthFilter.INSTANCE.name()).isEqualTo("length");
    assertThat(ListFilter.INSTANCE.name()).isEqualTo("list");
    assertThat(LowerFilter.INSTANCE.name()).isEqualTo("lower");
    assertThat(MaxFilter.INSTANCE.name()).isEqualTo("max");
    assertThat(MinFilter.INSTANCE.name()).isEqualTo("min");
    assertThat(ReplaceFilter.INSTANCE.name()).isEqualTo("replace");
    assertThat(ReverseFilter.INSTANCE.name()).isEqualTo("reverse");
    assertThat(RoundFilter.INSTANCE.name()).isEqualTo("round");
    assertThat(SafeFilter.INSTANCE.name()).isEqualTo("safe");
    assertThat(SelectFilter.INSTANCE.name()).isEqualTo("select");
    assertThat(SelectattrFilter.INSTANCE.name()).isEqualTo("selectattr");
    assertThat(RejectFilter.INSTANCE.name()).isEqualTo("reject");
    assertThat(RejectattrFilter.INSTANCE.name()).isEqualTo("rejectattr");
    assertThat(SortFilter.INSTANCE.name()).isEqualTo("sort");
    assertThat(StringFilter.INSTANCE.name()).isEqualTo("string");
    assertThat(TitleFilter.INSTANCE.name()).isEqualTo("title");
    assertThat(TrimFilter.INSTANCE.name()).isEqualTo("trim");
    assertThat(UniqueFilter.INSTANCE.name()).isEqualTo("unique");
    assertThat(UpperFilter.INSTANCE.name()).isEqualTo("upper");
    assertThat(UrlencodeFilter.INSTANCE.name()).isEqualTo("urlencode");
    assertThat(ValuesFilter.INSTANCE.name()).isEqualTo("values");
    assertThat(MapFilter.INSTANCE.name()).isEqualTo("map");
    assertThat(BatchFilter.INSTANCE.name()).isEqualTo("batch");
    assertThat(ToJsonFilter.INSTANCE.name()).isEqualTo("tojson");
    assertThat(IndentFilter.INSTANCE.name()).isEqualTo("indent");
    assertThat(StriptagsFilter.INSTANCE.name()).isEqualTo("striptags");
    assertThat(WordcountFilter.INSTANCE.name()).isEqualTo("wordcount");
    assertThat(TruncateFilter.INSTANCE.name()).isEqualTo("truncate");
    assertThat(AttrFilter.INSTANCE.name()).isEqualTo("attr");
    assertThat(EscapeFilter.INSTANCE.name()).isEqualTo("escape");
    assertThat(XmlattrFilter.INSTANCE.name()).isEqualTo("xmlattr");
    assertThat(GroupbyFilter.INSTANCE.name()).isEqualTo("groupby");
    assertThat(SumFilter.INSTANCE.name()).isEqualTo("sum");
    assertThat(CenterFilter.INSTANCE.name()).isEqualTo("center");
    assertThat(FilesizeformatFilter.INSTANCE.name()).isEqualTo("filesizeformat");
  }

  @Test
  void aliasesDeclared() {
    assertThat(DefaultFilter.INSTANCE.aliases()).containsExactly("d");
    assertThat(LengthFilter.INSTANCE.aliases()).containsExactly("count");
    assertThat(EscapeFilter.INSTANCE.aliases()).containsExactly("e", "forceescape");
  }

  @Test
  void filtersWithoutAliasesReturnEmpty() {
    List<NamedFilter> noAliasFilters = List.of(
      AbsFilter.INSTANCE,
      CapitalizeFilter.INSTANCE,
      UpperFilter.INSTANCE,
      LowerFilter.INSTANCE,
      TrimFilter.INSTANCE,
      ReverseFilter.INSTANCE
    );
    for (var f : noAliasFilters) {
      assertThat(f.aliases()).as("%s aliases", f.name()).isEmpty();
    }
  }

  @Test
  void singletonsAreStable() {
    assertThat(AbsFilter.INSTANCE).isSameAs(AbsFilter.INSTANCE);
    assertThat(UpperFilter.INSTANCE).isSameAs(UpperFilter.INSTANCE);
    assertThat(EscapeFilter.INSTANCE).isSameAs(EscapeFilter.INSTANCE);
  }
}
