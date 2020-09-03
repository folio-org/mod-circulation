package org.folio.circulation.support;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.folio.circulation.support.JsonPropertyWriter.writeByPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class JsonPropertyWriterTest {

  @Test
  public void shouldCreateMissingObjects() {
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
  public void shouldWriteStringByPath() {
    final String[] paths = {"1", "2", "3", "4", "5"};
    final String expectedString = "A string";
    final String json = "{\n" +
      "    \"1\": {\n" +
      "        \"2\": {\n" +
      "            \"3\": {\n" +
      "                \"4\": {}\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}";

    final JsonObject object = new JsonObject(json);
    writeByPath(object, expectedString, paths);

    assertThat(object, hasJsonPath("1.2.3.4.5", "A string"));
  }
}
