package org.folio.circulation.support.http.client;

import io.vertx.core.json.JsonObject;

public class Response {
  protected final String body;
  private final int statusCode;

  public Response(int statusCode, String body) {
    this.statusCode = statusCode;
    this.body = body;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public JsonObject getJson() {
    String body = getBody();

    if(body != null && body != "") {
      return new JsonObject(body);
    }
    else {
      return new JsonObject();
    }
  }
}
