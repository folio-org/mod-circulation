package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class InstanceRequestBuilder implements Builder {
  private final String title;
  private final UUID id;
  private final JsonArray contributors;

  public InstanceRequestBuilder(String title) {
    id = UUID.randomUUID();
    this.title = title;
    this.contributors = new JsonArray();
  }

  public InstanceRequestBuilder(UUID id, String title) {
    this.id = id;
    this.title = title;
    this.contributors = new JsonArray();
  }

  public InstanceRequestBuilder(UUID id, String title, JsonArray contributors) {
    this.id = id;
    this.title = title;
    this.contributors = contributors;
  }


  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("id", id.toString())
      .put("title", title)
      .put("source", "Local")
      .put("instanceTypeId", APITestSuite.booksInstanceTypeId().toString())
      .put("contributors", contributors);
  }

  public InstanceRequestBuilder withId(UUID id) {
    return new InstanceRequestBuilder(
      id,
      this.title
    );
  }

  public InstanceRequestBuilder withTitle(String title) {
    return new InstanceRequestBuilder(
      this.id,
      title
    );
  }

  public InstanceRequestBuilder withContributors(JsonArray contributors) {
    return new InstanceRequestBuilder(
      this.id,
      this.title,
      contributors
    );
  }
}
