package api.support.fixtures;

import static api.support.RestAssuredClient.from;
import static api.support.RestAssuredClient.get;
import static api.support.RestAssuredClient.post;
import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.InterfaceUrls.checkInByBarcodeUrl;
import static api.support.http.InterfaceUrls.checkOutByBarcodeUrl;
import static api.support.http.InterfaceUrls.declareLoanItemLostURL;
import static api.support.http.InterfaceUrls.loansUrl;
import static api.support.http.InterfaceUrls.overrideCheckOutByBarcodeUrl;
import static api.support.http.InterfaceUrls.overrideRenewalByBarcodeUrl;
import static api.support.http.InterfaceUrls.renewByBarcodeUrl;
import static api.support.http.InterfaceUrls.renewByIdUrl;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import api.support.CheckInByBarcodeResponse;
import api.support.MultipleJsonRecords;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.OverrideCheckOutByBarcodeRequestBuilder;
import api.support.builders.OverrideRenewalByBarcodeRequestBuilder;
import api.support.builders.RenewByBarcodeRequestBuilder;
import api.support.builders.RenewByIdRequestBuilder;
import api.support.http.CqlQuery;
import api.support.http.Limit;
import api.support.http.Offset;
import io.vertx.core.json.JsonObject;

public class LoansFixture {
  private final UsersFixture usersFixture;
  private final ServicePointsFixture servicePointsFixture;

  public LoansFixture(
    UsersFixture usersFixture,
    ServicePointsFixture servicePointsFixture) {

    this.usersFixture = usersFixture;
    this.servicePointsFixture = servicePointsFixture;
  }

  public IndividualResource createLoan(
    IndividualResource item,
    IndividualResource to) {

    DateTime loanDate = DateTime.now();

    return createLoan(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId())
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plusWeeks(3)));
  }

  public IndividualResource createLoan(
    IndividualResource item,
    IndividualResource to,
    DateTime loanDate) {

    return createLoan(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId())
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plusWeeks(3)));
  }

  public IndividualResource createLoan(LoanBuilder builder) {
    return new IndividualResource(attemptToCreateLoan(builder, 201));
  }

  public Response attemptToCreateLoan(
    IndividualResource item, IndividualResource to) {

    return attemptToCreateLoan(item, to, UNPROCESSABLE_ENTITY);
  }

  public Response attemptToCreateLoan(
    IndividualResource item, IndividualResource to, int expectedStatusCode) {

    return attemptToCreateLoan(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId()), expectedStatusCode);
  }

  public Response attemptToCreateLoan(
    LoanBuilder loanBuilder, int expectedStatusCode) {

    return from(post(loanBuilder.create(), loansUrl(),
      expectedStatusCode, "post-loan"));
  }

  public Response declareItemLost(DeclareItemLostRequestBuilder builder) {

    JsonObject request = builder.create();

    return from(
      post(request, declareLoanItemLostURL(builder.getLoanId().toString()),
        "declare-item-lost-request"));
  }

  public IndividualResource checkOutByBarcode(IndividualResource item)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return checkOutByBarcode(item, usersFixture.jessica());
  }

  public IndividualResource checkOutByBarcode(
    IndividualResource item,
    IndividualResource to)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .at(defaultServicePoint()));
  }

  public IndividualResource checkOutByBarcode(
    IndividualResource item,
    IndividualResource to,
    DateTime loanDate)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .on(loanDate)
      .at(defaultServicePoint()));
  }

  public IndividualResource checkOutByBarcode(
    CheckOutByBarcodeRequestBuilder builder) {

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
      .to(to)
      .at(UUID.randomUUID()));
  }

  public Response attemptCheckOutByBarcode(
    CheckOutByBarcodeRequestBuilder builder) {

    return attemptCheckOutByBarcode(422, builder);
  }

  public Response attemptCheckOutByBarcode(
    int expectedStatusCode,
    CheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return from(post(request, checkOutByBarcodeUrl(),
      expectedStatusCode, "check-out-by-barcode-request"));
  }

  public IndividualResource renewLoan(
    IndividualResource item,
    IndividualResource user) {

    JsonObject request = new RenewByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(from(post(request, renewByBarcodeUrl(), 200,
      "renewal-by-barcode-request")));
  }

  public IndividualResource overrideRenewalByBarcode(
    IndividualResource item,
    IndividualResource user, String comment, String dueDate) {

    JsonObject request =
      new OverrideRenewalByBarcodeRequestBuilder()
        .forItem(item)
        .forUser(user)
        .withComment(comment)
        .withDueDate(dueDate)
        .create();

    return new IndividualResource(from(post(request, overrideRenewalByBarcodeUrl(), 200,
      "override-renewal-by-barcode-request")));
  }

  public IndividualResource renewLoanById(
    IndividualResource item,
    IndividualResource user) {

    JsonObject request = new RenewByIdRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(from(post(request, renewByIdUrl(), 200,
      "renewal-by-id-request")));
  }

  public Response attemptRenewal(
    IndividualResource item,
    IndividualResource user) {

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

  public Response attemptOverride(
    IndividualResource item,
    IndividualResource user, String comment, String dueDate) {

    return attemptOverride(422, item, user, comment, dueDate);
  }

  public Response attemptOverride(
    int expectedStatusCode,
    IndividualResource item,
    IndividualResource user, String comment, String dueDate) {

    JsonObject request =
      new OverrideRenewalByBarcodeRequestBuilder()
        .forItem(item)
        .forUser(user)
        .withComment(comment)
        .withDueDate(dueDate)
        .create();

    return from(post(request, overrideRenewalByBarcodeUrl(),
      expectedStatusCode, "override-renewal-by-barcode-request"));
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

  public Response attemptCheckInByBarcode(
    CheckInByBarcodeRequestBuilder builder) {

    return from(post(builder.create(), checkInByBarcodeUrl(),
        "check-in-by-barcode-request"));
  }

  public CheckInByBarcodeResponse checkInByBarcode(
    CheckInByBarcodeRequestBuilder builder) {

    return new CheckInByBarcodeResponse(
      from(post(builder.create(), checkInByBarcodeUrl(), 200,
        "check-in-by-barcode-request")));
  }

  public CheckInByBarcodeResponse checkInByBarcode(IndividualResource item)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(defaultServicePoint()));
  }

  public CheckInByBarcodeResponse checkInByBarcode(
    IndividualResource item,
    DateTime checkInDate,
    UUID servicePointId) {

    return checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .on(checkInDate)
      .at(servicePointId));
  }

  private IndividualResource defaultServicePoint()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return servicePointsFixture.cd1();
  }

  public IndividualResource overrideCheckOutByBarcode(
    OverrideCheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return new IndividualResource(
      from(post(request, overrideCheckOutByBarcodeUrl(), 201,
        "override-check-out-by-barcode-request")));
  }

  public Response attemptOverrideCheckOutByBarcode(
    OverrideCheckOutByBarcodeRequestBuilder builder) {

    return attemptOverrideCheckOutByBarcode(422, builder);
  }

  public Response attemptOverrideCheckOutByBarcode(
    int expectedStatusCode,
    OverrideCheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return from(post(request, overrideCheckOutByBarcodeUrl(),
      expectedStatusCode, "override-check-out-by-barcode-request"));
  }

  public IndividualResource getLoanById(UUID id) {
    return new IndividualResource(from(get(
      loansUrl(String.format("/%s", id)), 200, "get-loan-by-id")));
  }

  public Response getLoans() {
    return getLoans(noQuery());
  }

  public Response getLoans(Limit limit) {
    return getLoans(noQuery(), limit, noOffset());
  }

  public Response getLoans(Limit limit, Offset offset) {
    return getLoans(noQuery(), limit, offset);
  }

  public Response getLoans(CqlQuery query) {
    return getLoans(query, noLimit(), noOffset());
  }

  public static Response getLoans(CqlQuery query, Limit limit, Offset offset) {
    final HashMap<String, String> queryStringParameters = new HashMap<>();

    Stream.of(query, limit, offset)
      .forEach(parameter -> parameter.collectInto(queryStringParameters));

    return from(get(loansUrl(), queryStringParameters, 200, "get-loans"));
  }

  public MultipleJsonRecords getAllLoans() {
    return new MultipleJsonRecords(
      JsonArrayHelper.mapToList(getLoans(Limit.maximumLimit()).getJson(), "loans"));
  }
}
