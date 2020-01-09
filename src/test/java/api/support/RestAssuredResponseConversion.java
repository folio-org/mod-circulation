package api.support;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.http.CaseInsensitiveHeaders;

public class RestAssuredResponseConversion {
  public static Response toResponse(io.restassured.response.Response response) {
    final CaseInsensitiveHeaders mappedHeaders = new CaseInsensitiveHeaders();

    response.headers().iterator().forEachRemaining(h -> {
      mappedHeaders.add(h.getName(), h.getValue());
    });

    return new Response(response.statusCode(), response.body().print(),
      response.contentType(), mappedHeaders, null);
  }
}
