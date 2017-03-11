package org.folio.circulation.support.http.client;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class IndividualResource {

  private final JsonResponse response;

  public IndividualResource(JsonResponse response) {
    this.response = response;
  }

  public UUID getId() {
    return UUID.fromString(response.getJson().getString("id"));
  }

  public JsonObject getJson() {
    return response.getJson().copy();
  }

  public JsonObject copyJson() {
    return response.getJson().copy();
  }
}
