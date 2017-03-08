package org.folio.circulation.support.http.client;

import io.vertx.core.json.JsonObject;

public class JsonResponse extends TextResponse {
  public JsonResponse(int statusCode, String body) {
    super(statusCode, body);
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

  public JsonObject copyJson() {
    return getJson().copy();
  }
}
