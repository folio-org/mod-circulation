package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class InstanceRequestBuilder implements Builder {
  private final String title;
  private final UUID id;

  public InstanceRequestBuilder(String title) {
    id = UUID.randomUUID();
    this.title = title;
  }

  public InstanceRequestBuilder(UUID id, String title) {
    this.id = id;
    this.title = title;
  }

  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("id", id.toString())
      .put("title", title)
      .put("source", "Local")
      .put("instanceTypeId", APITestSuite.booksInstanceTypeId().toString());
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
}
