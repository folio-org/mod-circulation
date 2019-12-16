package api.support;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static io.restassured.RestAssured.given;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.net.URL;
import java.util.Map;

import org.folio.circulation.support.http.OkapiHeader;
import org.folio.circulation.support.http.client.Response;

import api.support.http.OkapiHeaders;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;

public class RestAssuredClient {
  private static RequestSpecification standardHeaders(OkapiHeaders okapiHeaders) {
    return new RequestSpecBuilder()
      .addHeader(OKAPI_URL, okapiHeaders.getUrl().toString())
      .addHeader(TENANT, okapiHeaders.getTenantId())
      .addHeader(OkapiHeader.TOKEN, okapiHeaders.getToken())
      .addHeader(OkapiHeader.USER_ID, okapiHeaders.getUserId())
      .addHeader(OkapiHeader.REQUEST_ID, okapiHeaders.getRequestId())
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  private static RequestSpecification timeoutConfig() {
    final int defaultTimeOutInMilliseconds = 5000;

    return timeoutConfig(defaultTimeOutInMilliseconds);
  }

  private static RequestSpecification timeoutConfig(int timeOutInMilliseconds) {
    return new RequestSpecBuilder()
      .setConfig(RestAssured.config()
        .httpClient(HttpClientConfig.httpClientConfig()
          .setParam("http.connection.timeout", timeOutInMilliseconds)
          .setParam("http.socket.timeout", timeOutInMilliseconds)))
      .build();
  }

  public static Response from(io.restassured.response.Response response) {
    final CaseInsensitiveHeaders mappedHeaders = new CaseInsensitiveHeaders();

    response.headers().iterator().forEachRemaining(h -> {
      mappedHeaders.add(h.getName(), h.getValue());
    });

    return new Response(response.statusCode(), response.body().print(),
      response.contentType(), mappedHeaders, null);
  }

  public static io.restassured.response.Response manuallyStartTimedTask(
    URL url,
    int expectedStatusCode,
    String requestId) {

    return given()
      .log().all()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig(10000))
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response();
  }

  public static io.restassured.response.Response post(
    JsonObject representation,
    URL url,
    int expectedStatusCode,
    String requestId) {

    return given()
      .log().all()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response();
  }

  public static io.restassured.response.Response post(
    JsonObject representation,
    URL url,
    String requestId) {

    return given()
      .log().all()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .extract().response();
  }

  public static io.restassured.response.Response get(
    URL url,
    int expectedStatusCode,
    String requestId) {

    return given()
      .log().all()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response();
  }

  public static io.restassured.response.Response get(
    URL url, Map<String, String> queryStringParameters, int expectedStatusCode,
    String requestId) {

    return given()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .queryParams(queryStringParameters)
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response();
  }
}
