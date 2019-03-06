package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class Response {
  protected final String body;
  private final int statusCode;
  private final String contentType;
  private final CaseInsensitiveHeaders headers;

  public Response(int statusCode, String body, String contentType) {
    this(statusCode, body, contentType, new CaseInsensitiveHeaders());
  }

  public Response(
    int statusCode,
    String body,
    String contentType,
    CaseInsensitiveHeaders headers) {

    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
    this.headers = headers;
  }

  public static Response from(HttpClientResponse response, Buffer body) {
    final CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();

    headers.addAll(response.headers());

    return new Response(response.statusCode(),
      BufferHelper.stringFromBuffer(body),
      convertNullToEmpty(response.getHeader(CONTENT_TYPE)),
      headers);
  }

  public Response attachBody(String body){
    return new Response(statusCode, body, contentType, headers);
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
