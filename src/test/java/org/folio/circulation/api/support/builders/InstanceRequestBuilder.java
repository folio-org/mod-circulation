package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

public class InstanceRequestBuilder implements Builder {


  private final String title;
  private final String creator;

  public InstanceRequestBuilder(String title, String creator) {
    this.title = title;
    this.creator = creator;
  }

  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("title", title)
      .put("creators", new JsonArray().add(new JsonObject()
        .put("creatorTypeId", APITestSuite.personalCreatorTypeId().toString())
        .put("name", creator)))
      .put("source", "Local")
      .put("instanceTypeId", APITestSuite.booksInstanceTypeId().toString());
  }
}
