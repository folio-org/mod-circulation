package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class InstanceRequestBuilder implements Builder {
  private final String title;
  private final String creator;
  private final UUID id;

  public InstanceRequestBuilder(String title, String creator) {
    id = UUID.randomUUID();
    this.title = title;
    this.creator = creator;
  }

  public InstanceRequestBuilder(UUID id, String title, String creator) {
    this.id = id;
    this.title = title;
    this.creator = creator;
  }

  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("id", id.toString())
      .put("title", title)
      .put("creators", new JsonArray().add(new JsonObject()
        .put("creatorTypeId", APITestSuite.personalCreatorTypeId().toString())
        .put("name", creator)))
      .put("source", "Local")
      .put("instanceTypeId", APITestSuite.booksInstanceTypeId().toString());
  }

  public InstanceRequestBuilder withId(UUID id) {
    return new InstanceRequestBuilder(
      id,
      this.title,
      this.creator);
  }

  public InstanceRequestBuilder withTitle(String title) {
    return new InstanceRequestBuilder(
      this.id,
      title,
      this.creator);
  }
}
