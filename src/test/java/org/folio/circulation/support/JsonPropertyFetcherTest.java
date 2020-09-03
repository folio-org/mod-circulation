package org.folio.circulation.support;

import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;

import org.joda.time.DateTime;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class JsonPropertyFetcherTest {

  @Test
  public void shouldReturnNullIfSomeObjectsNotExistInPath() {
    final String[] paths = {"1", "2", "3", "4", "5"};
    final JsonObject object = new JsonObject()
      .put("1", new JsonObject().put("2", new JsonObject()));

    final DateTime actualValue = getDateTimePropertyByPath(object, paths);

    assertThat(actualValue, nullValue());
  }

  @Test
  public void shouldReturnDateTimePropertyByPath() {
    final String[] paths = {"1", "2", "3", "4", "5"};
    final String expectedDate = "2020-12-12T20:00:00.123Z";
    final String json = "{\n" +
      "    \"1\": {\n" +
      "        \"2\": {\n" +
      "            \"3\": {\n" +
      "                \"4\": {\n" +
      "                    \"5\": \"" + expectedDate + "\"\n" +
      "                }\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}";

    final DateTime actualValue = getDateTimePropertyByPath(new JsonObject(json), paths);
    assertThat(actualValue, is(DateTime.parse("2020-12-12T20:00:00.123Z")));
  }
}
