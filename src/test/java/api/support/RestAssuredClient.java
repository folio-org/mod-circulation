package api.support;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.RestAssuredConfiguration.defaultRestAssuredConfig;
import static api.support.RestAssuredConfiguration.standardHeaders;
import static api.support.RestAssuredConfiguration.timeoutConfig;
import static api.support.RestAssuredResponseConversion.toResponse;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.greaterThan;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.circulation.support.http.client.Response;

import api.support.http.CqlQuery;
import api.support.http.Limit;
import api.support.http.Offset;
import api.support.http.OkapiHeaders;
import api.support.http.QueryStringParameter;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;

public class RestAssuredClient {
  private final OkapiHeaders defaultHeaders;
  private final RestAssuredConfig config;

  public RestAssuredClient(OkapiHeaders defaultHeaders) {
    this(defaultHeaders, defaultRestAssuredConfig());
  }

  public RestAssuredClient(OkapiHeaders defaultHeaders, RestAssuredConfig config) {
    this.defaultHeaders = defaultHeaders;
    this.config = config;
  }

  public static RestAssuredClient defaultRestAssuredClient() {
    return new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public RequestSpecification beginRequest(String requestId) {
    return given()
      .log().ifValidationFails()
      .config(config)
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig());
  }

  public Response get(URL location, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().get(location)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().response());
  }

  public <T> T get(URL location, String requestId, Class<T> type) {
    return beginRequest(requestId)
      .when().get(location)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().body().as(type);
  }

  public Response get(URL location, CqlQuery query, Limit limit, Offset offset,
      int expectedStatusCode, String requestId) {

    return get(location, asList(query, limit, offset), expectedStatusCode,
        requestId);
  }

  public Response get(URL url, Collection<QueryStringParameter> parameters,
      int expectedStatusCode, String requestId) {

    return toResponse(given()
      .config(config)
      .log().ifValidationFails()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .queryParams(toMap(parameters))
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public <T> List<T> getMany(URL url, Class<T> type, String collectionName, QueryStringParameter... parameters) {
    return given()
      .config(config)
      .log().ifValidationFails()
      .spec(standardHeaders(defaultHeaders.withRequestId("get-many-" + collectionName)))
      .queryParams(toMap(asList(parameters)))
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .extract().jsonPath().getList(collectionName, type);
  }

  public Response get(URL url, int expectedStatusCode, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().get(url)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(URL url, int expectedStatusCode, String requestId,
      Integer timeoutInMilliseconds) {

    final RequestSpecification timeoutConfig = timeoutInMilliseconds != null
      ? timeoutConfig(timeoutInMilliseconds)
      : timeoutConfig();

    return toResponse(given()
      .config(config)
      .log().ifValidationFails()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig)
      .when().post(url)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(URL url, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().post(url)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().response());
  }

  public Response post(Object representation, URL url, String requestId) {
    return toResponse(beginRequest(requestId)
      .body(representation)
      .when().post(url)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().response());
  }

  public Response post(Object representation, URL url,
    int expectedStatusCode, String requestId) {

    return toResponse(beginRequest(requestId)
      .body(representation)
      .when().post(url)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(Object representation, URL location,
    int expectedStatusCode, OkapiHeaders okapiHeaders) {

    return toResponse(given()
      .config(config)
      .log().ifValidationFails()
      .spec(standardHeaders(okapiHeaders))
      .spec(timeoutConfig())
      .body(representation)
      .when().post(location)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(Object representation, URL location, OkapiHeaders okapiHeaders) {
    return toResponse(given()
      .config(config)
      .log().ifValidationFails()
      .spec(standardHeaders(okapiHeaders))
      .spec(timeoutConfig())
      .body(representation)
      .when().post(location)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().response());
  }

  public Response put(Object representation, URL location, String requestId) {
    return toResponse(beginRequest(requestId)
      .body(representation)
      .when().put(location)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().response());
  }

  public Response put(Object representation, URL location,
    int expectedStatusCode, String requestId) {

    return toResponse(beginRequest(requestId)
      .body(representation)
      .when().put(location)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response delete(URL location, int expectedStatusCode, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().delete(location)
      .then()
      .log().ifValidationFails()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response delete(URL location, String requestId) {
    return toResponse(beginRequest(requestId)
      .when().delete(location)
      .then()
      .log().ifStatusCodeMatches(greaterThan(300))
      .extract().response());
  }

  private Map<String, String> toMap(Iterable<QueryStringParameter> params) {
    final Map<String, String> paramsMap = new HashMap<>();
    params.forEach(param -> param.collectInto(paramsMap));
    return paramsMap;
  }
}
