package org.folio.circulation.support.json;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static java.util.stream.Stream.of;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.util.List;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.val;

class JsonObjectArrayPropertyFetcherTests {
  @Test
  void StreamShouldContainSameContentsAsArray() {
    val json = objectWithJsonArrayOf(createObjectWithName("Foo"),
      createObjectWithName("Bar"), createObjectWithName("Lorem"), createObjectWithName("Ipsum"));

    assertThat(toList(toStream(json, "array")), contains(objectWithName("Foo"),
      objectWithName("Bar"), objectWithName("Lorem"), objectWithName("Ipsum")));
  }

  @Test
  void shouldMapEmptyArrayToEmptyStream() {
    val json = objectWithJsonArrayOf();

    assertThat(toList(toStream(json, "array")), is(empty()));
  }

  @Test
  void shouldSkipNonObjectElements() {
    val array = new JsonArray(toList(of(createObjectWithName("Foo"), "Bar",
      createObjectWithName("Lorem"), createObjectWithName("Ipsum"))));

    val json = new JsonObject().put("array", array);

    assertThat(toList(toStream(json, "array")),
      contains(objectWithName("Foo"), objectWithName("Lorem"), objectWithName("Ipsum")));
  }

  @Test
  void ListShouldContainMappedContents() {
    val json = objectWithJsonArrayOf(createObjectWithName("Foo"),
      createObjectWithName("Bar"), createObjectWithName("Lorem"), createObjectWithName("Ipsum"));

    List<String> list = mapToList(json, "array",
      JsonObjectArrayPropertyFetcherTests::getName);

    assertThat(list, contains("Foo", "Bar", "Lorem", "Ipsum"));
  }

  private static String getName(JsonObject object) {
    return getProperty(object, "name");
  }

  private JsonObject objectWithJsonArrayOf(JsonObject... objects) {
    val array = jsonArrayOf(objects);

    return new JsonObject().put("array", array);
  }

  private JsonArray jsonArrayOf(JsonObject... objects) {
    return new JsonArray(toList(of(objects)));
  }

  private JsonObject createObjectWithName(String name) {
    return new JsonObject().put("name", name);
  }

  private Matcher<JsonObject> objectWithName(String name) {
    return hasJsonPath("name", is(name));
  }
}
