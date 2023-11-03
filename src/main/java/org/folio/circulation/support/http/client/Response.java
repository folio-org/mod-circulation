package org.folio.circulation.support.http.client;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.lang.String.format;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import lombok.ToString;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.ContentType;
@ToString(onlyExplicitlyIncluded = true)
public class Response {
  @ToString.Include
  protected final String body;
  @ToString.Include
  private final int statusCode;
  private final String contentType;
  private final MultiMap headers;
  private final String fromUrl;

  public Response(int statusCode, String body, String contentType) {
    this(statusCode, body, contentType, caseInsensitiveMultiMap(), null);
  }

  public Response(int statusCode, String body, String contentType,
    MultiMap headers, String fromUrl) {

    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
    this.headers = headers;
    this.fromUrl = fromUrl;
  }

  static Response responseFrom(String url, HttpResponse<Buffer> response) {
    val headers = caseInsensitiveMultiMap();

    headers.addAll(response.headers());

    return new Response(response.statusCode(), response.bodyAsString(),
      headers.get(ContentType.CONTENT_TYPE), headers, url);
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

  public String getHeader(String name) {
    return headers.get(name);
  }

  String getFromUrl() {
    return fromUrl;
  }

  @Override
  public String toString() {
    return format(
      "Response from \"%s\" status code: %s body: \"%s\", content type: \"%s\"",
        getFromUrl(), getStatusCode(), getBody(), getContentType());
  }
}
