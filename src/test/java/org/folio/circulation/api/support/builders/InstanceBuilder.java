package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class InstanceBuilder implements Builder {
  private final String title;
  private final UUID id;

  public InstanceBuilder(String title) {
    id = UUID.randomUUID();
    this.title = title;
  }

  public InstanceBuilder(UUID id, String title) {
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

  public InstanceBuilder withId(UUID id) {
    return new InstanceBuilder(
      id,
      this.title
    );
  }

  public InstanceBuilder withTitle(String title) {
    return new InstanceBuilder(
      this.id,
      title
    );
  }
}
