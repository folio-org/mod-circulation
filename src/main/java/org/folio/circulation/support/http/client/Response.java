package org.folio.circulation.support.http.client;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

public class Response {
  protected final String body;
  private final int statusCode;
  private final String contentType;
  private final CaseInsensitiveHeaders headers;
  private final String fromUrl;

  public Response(int statusCode, String body, String contentType) {
    this(statusCode, body, contentType, new CaseInsensitiveHeaders(), null);
  }

  public Response(
    int statusCode,
    String body,
    String contentType,
    CaseInsensitiveHeaders headers,
    String fromUrl) {

    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
    this.headers = headers;
    this.fromUrl = fromUrl;
  }

  public static Response from(HttpClientResponse response, Buffer body) {
    return from(response, body, null);
  }

  public static Response from(HttpClientResponse response, Buffer body,
                              String fromUrl) {

    final CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();

    headers.addAll(response.headers());

    return new Response(response.statusCode(),
      BufferHelper.stringFromBuffer(body),
      convertNullToEmpty(response.getHeader(CONTENT_TYPE)),
      headers, fromUrl);
  }

  public boolean hasBody() {
    return StringUtils.isNotBlank(getBody());
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

  String getHeader(String name) {
    return headers.get(name);
  }

  public String getFromUrl() {
    return fromUrl;
  }
}
