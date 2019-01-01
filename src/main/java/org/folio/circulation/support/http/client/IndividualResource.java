package org.folio.circulation.support.http.client;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class IndividualResource {
  private final Response response;

  public IndividualResource(Response response) {
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

  public String getLocation() {
    return response.getHeader("location");
  }

  public Response getResponse() {
    return response;
  }
}
