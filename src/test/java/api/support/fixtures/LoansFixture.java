package api.support.fixtures;

import api.APITestSuite;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.RenewByBarcodeRequestBuilder;
import api.support.http.ResourceClient;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static api.support.http.InterfaceUrls.*;
import static io.restassured.RestAssured.given;
import static org.folio.circulation.support.http.OkapiHeader.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoansFixture {
  private final ResourceClient loansClient;
  private final OkapiHttpClient client;

  public LoansFixture(ResourceClient loansClient, OkapiHttpClient client) {
    this.loansClient = loansClient;
    this.client = client;
  }

  public IndividualResource checkOut(IndividualResource item, IndividualResource to)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
    return checkOut(item, to, DateTime.now());
  }

  public IndividualResource checkOut(
    IndividualResource item,
    IndividualResource to,
    DateTime loanDate)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loansClient.create(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId())
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plusWeeks(3)));
  }

  public IndividualResource checkOutItem(UUID itemId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loansClient.create(new LoanBuilder()
      .open()
      .withItemId(itemId));
  }

  public void renewLoan(UUID loanId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = loansClient.getById(loanId);

    //TODO: Should also change the due date
    JsonObject renewedLoan = getResponse.getJson().copy()
      .put("action", "renewed")
      .put("renewalCount", 1);

    loansClient.replace(loanId, renewedLoan);
  }

  public void checkIn(IndividualResource loan)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    checkInLoan(loan.getId());
  }

  public void checkInLoan(UUID loanId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = loansClient.getById(loanId);

    //TODO: Should also have a return date
    JsonObject closedLoan = getResponse.getJson().copy()
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin");

    loansClient.replace(loanId, closedLoan);
  }

  public Response attemptCheckOut(
    IndividualResource item,
    IndividualResource to)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(loansUrl(), new LoanBuilder()
        .open()
        .withItemId(item.getId())
        .withUserId(to.getId()).create(),
      ResponseHandler.json(createCompleted));

    final Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Should not be able to create loan: %s", response.getBody()),
      response.getStatusCode(), is(UNPROCESSABLE_ENTITY));

    return response;
  }

  public IndividualResource checkOutByBarcode(
    IndividualResource item,
    IndividualResource to) {

    return checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to));
  }

  public IndividualResource checkOutByBarcode(
    IndividualResource item,
    IndividualResource to,
    DateTime loanDate) {

    return checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .at(loanDate));
  }

  public IndividualResource checkOutByBarcode(CheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return new IndividualResource(
      from(post(request, checkOutByBarcodeUrl(), 201,
        "check-out-by-barcode-request")));
  }

  public Response attemptCheckOutByBarcode(
    IndividualResource item,
    IndividualResource to) {

    return attemptCheckOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to));
  }

  public Response attemptCheckOutByBarcode(CheckOutByBarcodeRequestBuilder builder) {
    return attemptCheckOutByBarcode(422, builder);
  }

  public Response attemptCheckOutByBarcode(
    int expectedStatusCode, CheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return from(post(request, checkOutByBarcodeUrl(),
      expectedStatusCode, "check-out-by-barcode-request"));
  }

  private RequestSpecification defaultHeaders(String requestId) {
    return new RequestSpecBuilder()
      .addHeader(OKAPI_URL, APITestSuite.okapiUrl().toString())
      .addHeader(TENANT, APITestSuite.TENANT_ID)
      .addHeader(TOKEN, APITestSuite.TOKEN)
      .addHeader(USER_ID, APITestSuite.USER_ID)
      .addHeader(REQUEST_ID, requestId)
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  private RequestSpecification timeoutConfig() {
    return new RequestSpecBuilder()
      .setConfig(RestAssured.config()
        .httpClient(HttpClientConfig.httpClientConfig()
          .setParam("http.connection.timeout", 5000)
          .setParam("http.socket.timeout", 5000))).build();
  }

  private static Response from(io.restassured.response.Response response) {
    final CaseInsensitiveHeaders mappedHeaders = new CaseInsensitiveHeaders();

    response.headers().iterator().forEachRemaining(h -> {
      mappedHeaders.add(h.getName(), h.getValue());
    });

    return new Response(response.statusCode(), response.body().print(),
      response.contentType(),
      mappedHeaders);
  }

  public IndividualResource renewLoan(IndividualResource item, IndividualResource user) {
    JsonObject request = new RenewByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(from(post(request, renewByBarcodeUrl(), 200,
      "renewal-by-barcode-request")));
  }

  public Response attemptRenewal(IndividualResource item, IndividualResource user) {
    return attemptRenewal(422, item, user);
  }

  public Response attemptRenewal(
    int expectedStatusCode,
    IndividualResource item,
    IndividualResource user) {

    JsonObject request = new RenewByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return from(post(request, renewByBarcodeUrl(),
      expectedStatusCode, "renewal-by-barcode-request"));
  }

  private io.restassured.response.Response post(
    JsonObject representation,
    URL url,
    int expectedStatusCode) {
    return post(representation, url, expectedStatusCode,
      APITestSuite.REQUEST_ID);
  }

  private io.restassured.response.Response post(
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
}
