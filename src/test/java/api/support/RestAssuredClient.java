package api.support;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.RestAssuredConfiguration.standardHeaders;
import static api.support.RestAssuredConfiguration.timeoutConfig;
import static api.support.RestAssuredResponseConversion.toResponse;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;

import org.folio.circulation.support.http.client.Response;

import api.support.http.CqlQuery;
import api.support.http.Limit;
import api.support.http.Offset;
import api.support.http.OkapiHeaders;
import api.support.http.QueryStringParameter;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

public class RestAssuredClient {
  private final OkapiHeaders defaultHeaders;

  public RestAssuredClient(OkapiHeaders defaultHeaders) {
    this.defaultHeaders = defaultHeaders;
  }

  public RequestSpecification beginRequest(String requestId) {
    return given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig());
  }

  public Response get(URL location, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().get(location)
      .then()
      .log().all()
      .extract().response());
  }

  public Response get(URL location, CqlQuery query, Limit limit, Offset offset,
      int expectedStatusCode, String requestId) {

    return get(location, asList(query, limit, offset), expectedStatusCode,
        requestId);
  }

  public Response get(URL url, Collection<QueryStringParameter> parameters,
      int expectedStatusCode, String requestId) {

    final HashMap<String, String> queryStringParametersMap = new HashMap<>();

    parameters
      .forEach(parameter -> parameter.collectInto(queryStringParametersMap));

    return toResponse(given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .queryParams(queryStringParametersMap)
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response get(URL url, int expectedStatusCode, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(URL url, int expectedStatusCode, String requestId,
      Integer timeoutInMilliseconds) {

    final RequestSpecification timeoutConfig = timeoutInMilliseconds != null
      ? timeoutConfig(timeoutInMilliseconds)
      : timeoutConfig();

    return toResponse(given()
      .log().all()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig)
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(JsonObject representation, URL url, String requestId) {
    return toResponse(beginRequest(requestId)
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .extract().response());
  }

  public Response post(JsonObject representation, URL url,
    int expectedStatusCode, String requestId) {

    return toResponse(beginRequest(requestId)
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(JsonObject representation, URL location,
    int expectedStatusCode, OkapiHeaders okapiHeaders) {

    return toResponse(given()
      .log().all()
      .spec(standardHeaders(okapiHeaders))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(location)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response put(JsonObject representation, URL location, String requestId) {
    return toResponse(beginRequest(requestId)
      .body(representation.encodePrettily())
      .when().put(location)
      .then()
      .log().all()
      .extract().response());
  }

  public Response put(JsonObject representation, URL location,
    int expectedStatusCode, String requestId) {

    return toResponse(beginRequest(requestId)
      .body(representation.encodePrettily())
      .when().put(location)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response delete(URL location, int expectedStatusCode, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().delete(location)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }
}
