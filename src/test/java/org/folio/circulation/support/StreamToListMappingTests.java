package org.folio.circulation.support;

import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import lombok.val;

class StreamToListMappingTests {
  @Test
  void listShouldContainSameContentsAsStream() {
    val stream = Stream.of("Foo", "Bar", "Lorem", "Ipsum");

    assertThat(toList(stream), contains("Foo", "Bar", "Lorem", "Ipsum"));
  }

  @Test
  void shouldMapEmptyStreamToEmptyList() {
    val emptyStream = Stream.empty();

    assertThat(toList(emptyStream), is(empty()));
  }

  @Test
  void shouldMapNullToEmptyList() {
    assertThat(toList(null), is(empty()));
  }
}
