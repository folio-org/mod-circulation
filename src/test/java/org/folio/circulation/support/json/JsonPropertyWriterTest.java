package org.folio.circulation.support.json;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class JsonPropertyWriterTest {
  @Test
  void shouldCreateMissingObjectsInThePath() {
    final String[] paths = {"1", "2", "3", "4", "5"};
    final JsonObject object2 = new JsonObject()
      .put("21", 2.1)
      .put("2", new JsonObject());

    final JsonObject object = new JsonObject()
      .put("11", "1.1")
      .put("1", object2);

    writeByPath(object, "5", paths);

    assertThat(object, allOf(
      hasJsonPath("1.2.3.4.5", "5"),
      hasJsonPath("11", "1.1"),
      hasJsonPath("1.21", 2.1)
    ));
  }

  @Test
  void shouldWriteCollection() {
    final var json = new JsonObject();
    final var value = List.of("foo", "bar");

    write(json, "collection", value);

    final var collection = toList(toStream(json, "collection"));

    assertThat(collection, contains("foo", "bar"));
  }

  @Test
  void shouldNotWriteEmptyCollection() {
    final var json = new JsonObject();

    write(json, "collection", Collections.emptyList());

    assertThat("collection property should not be present",
      json.containsKey("collection"), is(false));
  }

  @Test
  void shouldNotWriteNullCollection() {
    final var json = new JsonObject();

    write(json, "collection", (Collection<String>)null);

    assertThat("collection property should not be present",
      json.containsKey("collection"), is(false));
  }
}
