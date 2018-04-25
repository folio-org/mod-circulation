package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class Response {
  protected final String body;
  private final int statusCode;
  private final String contentType;
  private final Map<String, String> headers;

  public Response(int statusCode, String body, String contentType) {
    this(statusCode, body, contentType, new HashMap<>());
  }

  public Response(
    int statusCode,
    String body,
    String contentType,
    Map<String, String> headers) {

    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
    this.headers = headers;
  }

  public static Response from(HttpClientResponse response, Buffer body) {
    return new Response(response.statusCode(),
      BufferHelper.stringFromBuffer(body),
      convertNullToEmpty(response.getHeader(CONTENT_TYPE)),
      response.headers().entries().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  public boolean hasBody() {
    return getBody() != null && getBody().trim() != "";
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public JsonObject getJson() {
    if(hasBody()) {
      return new JsonObject(getBody());
    }
    else {
      return new JsonObject();
    }
  }

  public String getContentType() {
    return contentType;
  }

  private static String convertNullToEmpty(String text) {
    return text != null ? text : "";
  }

  public String getHeader(String name) {
    return headers.get(name);
  }
}
