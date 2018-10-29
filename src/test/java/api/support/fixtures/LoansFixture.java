package api.support.fixtures;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.RenewByBarcodeRequestBuilder;
import api.support.builders.RenewByIdRequestBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.RestAssuredClient.from;
import static api.support.RestAssuredClient.post;
import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static api.support.http.InterfaceUrls.*;
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

  public IndividualResource checkOutItem(UUID itemId, UUID loanId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loansClient.create(new LoanBuilder()
      .withId(loanId)
      .open()
      .withItemId(itemId));
  }
  
  public IndividualResource checkOutItem(UUID itemId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
    
    return checkOutItem(itemId, UUID.randomUUID());
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

    //TODO: Remplace with attemptCreate
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

  public IndividualResource renewLoan(IndividualResource item, IndividualResource user) {
    JsonObject request = new RenewByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(from(post(request, renewByBarcodeUrl(), 200,
      "renewal-by-barcode-request")));
  }

  public IndividualResource renewLoanById(IndividualResource item, IndividualResource user) {
    JsonObject request = new RenewByIdRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(from(post(request, renewByIdUrl(), 200,
      "renewal-by-id-request")));
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

  public Response attemptRenewalById(
    IndividualResource item,
    IndividualResource user) {

    JsonObject request = new RenewByIdRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return from(post(request, renewByIdUrl(),
      422, "renewal-by-id-request"));
  }

}
