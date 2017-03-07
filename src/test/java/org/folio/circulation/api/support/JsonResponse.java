package org.folio.circulation.api.support;

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
}
