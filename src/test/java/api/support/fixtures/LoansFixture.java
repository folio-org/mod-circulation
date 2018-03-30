package api.support.fixtures;

import api.APITestSuite;
import api.support.builders.LoanBuilder;
import api.support.http.InterfaceUrls;
import api.support.http.ResourceClient;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static org.folio.circulation.support.http.OkapiHeader.*;
import static guru.nidi.ramltester.junit.RamlMatchers.hasNoViolations;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class LoansFixture {

  private final ResourceClient loansClient;
  private final OkapiHttpClient client;
  private final RestAssuredClient restAssuredClient = createClient();

  public LoansFixture(ResourceClient loansClient, OkapiHttpClient client) {
    this.loansClient = loansClient;
    this.client = client;
  }

  public IndividualResource checkOut(IndividualResource item, IndividualResource to)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loansClient.create(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId()));
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

    client.post(InterfaceUrls.loansUrl(), new LoanBuilder()
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

  public IndividualResource checkOutByBarcode(IndividualResource item, IndividualResource to) {
    final String itemBarcode = getBarcode(item);
    final String userBarcode = getBarcode(to);

    JsonObject request = checkOutByBarcodeRequest(itemBarcode, userBarcode);

    io.restassured.response.Response response = restAssuredClient.given()
      .log().all()
      .spec(defaultHeaders())
      .body(request.encodePrettily())
      .when().post(InterfaceUrls.checkOutUrl())
      .then()
      .log().all()
      .statusCode(201)
      .extract().response();

    assertThat(restAssuredClient.getLastReport().toString(),
      restAssuredClient.getLastReport(), hasNoViolations());

    return new IndividualResource(from(response));
  }

  public Response attemptCheckOutByBarcode(
    IndividualResource item,
    IndividualResource to) {

    final String itemBarcode = getBarcode(item);
    final String userBarcode = getBarcode(to);

    JsonObject request = checkOutByBarcodeRequest(itemBarcode, userBarcode);

    io.restassured.response.Response response = restAssuredClient.given()
      .log().all()
      .spec(defaultHeaders())
      .body(request.encodePrettily())
      .when().post(InterfaceUrls.checkOutUrl())
      .then()
      .log().all()
      .statusCode(422)
      .extract().response();

    assertThat(restAssuredClient.getLastReport().toString(),
      restAssuredClient.getLastReport(), hasNoViolations());

    return from(response);
  }

  private RequestSpecification defaultHeaders() {
    return new RequestSpecBuilder()
      .addHeader(OKAPI_URL, APITestSuite.okapiUrl().toString())
      .addHeader(TENANT, APITestSuite.TENANT_ID)
      .addHeader(TOKEN, APITestSuite.TOKEN)
      .addHeader(REQUEST_ID, APITestSuite.REQUEST_ID)
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }

  private JsonObject checkOutByBarcodeRequest(String itemBarcode, String userBarcode) {
    return new JsonObject()
      .put("itemBarcode", itemBarcode)
      .put("userBarcode", userBarcode);
  }

  private static Response from(io.restassured.response.Response response) {
    return new Response(response.statusCode(), response.body().print(),
      response.contentType());
  }

  private RestAssuredClient createClient() {
    RamlDefinition api = RamlLoaders.fromFile("ramls")
      .load("circulation.raml")
      .ignoringXheaders();

    return api.createRestAssured3();
  }
}
