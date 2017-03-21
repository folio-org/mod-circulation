package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class Response {
  protected final String body;
  private final int statusCode;
  private final String contentType;

  public Response(int statusCode, String body, String contentType) {
    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
  }

  public static Response from(HttpClientResponse response, Buffer body) {
    return new Response(response.statusCode(),
      BufferHelper.stringFromBuffer(body),
      response.getHeader(CONTENT_TYPE));
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

  public String getContentType() {
    return contentType;
  }
}
