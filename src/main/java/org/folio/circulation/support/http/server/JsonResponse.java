package org.folio.circulation.support.http.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.loanrules.LoanRulesException;

public class JsonResponse {
  private JsonResponse() { }

  public static void loanRulesError(HttpServerResponse response, LoanRulesException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());
    body.put("line", e.getLine());
    body.put("column", e.getColumn());
    response(response, body);
  }

  public static void loanRulesError(HttpServerResponse response, DecodeException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());  // already contains line and column number
    response(response, body);
  }

  public static void response(HttpServerResponse response, JsonObject body) {
    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(422);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    response.write(buffer);
    response.end();
  }
}
