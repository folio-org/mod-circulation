package api.support;

import api.APITestSuite;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.OkapiHeader;
import org.folio.circulation.support.http.client.Response;

import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

//TODO: Make methods non-static
public class RestAssuredClient {
  private static RequestSpecification defaultHeaders(String requestId) {
    return new RequestSpecBuilder()
      .addHeader(OKAPI_URL, APITestSuite.okapiUrl().toString())
      .addHeader(TENANT, APITestSuite.TENANT_ID)
      .addHeader(OkapiHeader.TOKEN, APITestSuite.TOKEN)
      .addHeader(OkapiHeader.USER_ID, APITestSuite.USER_ID)
      .addHeader(OkapiHeader.REQUEST_ID, requestId)
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  private static RequestSpecification timeoutConfig() {
    return new RequestSpecBuilder()
      .setConfig(RestAssured.config()
        .httpClient(HttpClientConfig.httpClientConfig()
          .setParam("http.connection.timeout", 5000)
          .setParam("http.socket.timeout", 5000))).build();
  }

  public static Response from(io.restassured.response.Response response) {
    final CaseInsensitiveHeaders mappedHeaders = new CaseInsensitiveHeaders();

    response.headers().iterator().forEachRemaining(h -> {
      mappedHeaders.add(h.getName(), h.getValue());
    });

    return new Response(response.statusCode(), response.body().print(),
      response.contentType(),
      mappedHeaders);
  }

  public static io.restassured.response.Response post(
    JsonObject representation,
    URL url,
    int expectedStatusCode,
    String requestId) {

    return given()
      .log().all()
      .spec(defaultHeaders(requestId))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response();
  }

  public static io.restassured.response.Response get(
    URL url,
    int expectedStatusCode,
    String requestId) {

    return given()
      .log().all()
      .spec(defaultHeaders(requestId))
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response();
  }
}
