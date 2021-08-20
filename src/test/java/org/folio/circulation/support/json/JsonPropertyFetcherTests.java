package org.folio.circulation.support.json;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class JsonPropertyFetcherTests {
  @Test
  void shouldReturnDateTimePropertyByPath() {
    final String[] paths = {"1", "2", "3", "4", "5"};
    final DateTime expectedDate = DateTime.parse("2020-12-12T20:00:00.123Z");

    final JsonObject object = new JsonObject();
    writeByPath(object, expectedDate, paths);

    final DateTime actualValue = getDateTimePropertyByPath(object, paths);
    assertThat(actualValue, is(expectedDate));
  }

  @Test
  void shouldReturnNullWhenObjectsInThePathAreNotPresent() {
    final String[] paths = {"1", "2", "3", "4", "5"};
    final JsonObject object = new JsonObject()
      .put("1", new JsonObject().put("2", new JsonObject()));

    final DateTime actualValue = getDateTimePropertyByPath(object, paths);

    assertThat(actualValue, nullValue());
  }

  @Test
  void shouldReturnNullIfPropertyIsNull() {
    final String[] paths = {"1", "2"};
    final JsonObject object = new JsonObject()
      .put("1", new JsonObject().put("2", (String) null));

    final DateTime actualValue = getDateTimePropertyByPath(object, paths);

    assertThat(actualValue, nullValue());
  }
}
