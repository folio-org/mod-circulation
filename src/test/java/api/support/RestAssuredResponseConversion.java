package api.support;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;

import org.folio.circulation.support.http.client.Response;

import lombok.val;

public class RestAssuredResponseConversion {
  public static Response toResponse(io.restassured.response.Response response) {
    val mappedHeaders = caseInsensitiveMultiMap();

    response.headers().iterator().forEachRemaining(h -> {
      mappedHeaders.add(h.getName(), h.getValue());
    });

    return new Response(response.statusCode(), response.body().asString(),
      response.contentType(), mappedHeaders, null);
  }
}
