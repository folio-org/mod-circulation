package org.folio.circulation.support.http.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.loanrules.LoanRulesException;

import java.util.List;

public class JsonResponse {
  private JsonResponse() { }

  public static void success(
    HttpServerResponse response,
    JsonObject body) {

    success(response, body, null);
  }

  public static void success(
    HttpServerResponse response,
    JsonObject body,
    String location) {

    response(response, body, 200, location);
  }

  public static void unprocessableEntity(
    HttpServerResponse response,
    List<ValidationError> errors) {

    JsonArray errorsArray = new JsonArray();

    errors.forEach(error -> errorsArray.add(error.toJson()));

    response(response, new JsonObject().put("errors", errors), 422);
  }

  public static void loanRulesError(HttpServerResponse response, LoanRulesException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());
    body.put("line", e.getLine());
    body.put("column", e.getColumn());
    response(response, body, 422);
  }

  public static void loanRulesError(HttpServerResponse response, DecodeException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());  // already contains line and column number
    response(response, body, 422);
  }

  private static void response(
    HttpServerResponse response,
    JsonObject body,
    int statusCode,
    String location) {

    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(statusCode);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    if(StringUtils.isNotBlank(location)) {
      response.putHeader("location", location);
    }

    response.write(buffer);
    response.end();
  }

  public static void response(
    HttpServerResponse response,
    JsonObject body,
    int statusCode) {

    response(response, body, statusCode, null);

  }
}
