package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.MultipleJsonRecords.multipleRecordsFrom;
import static api.support.http.AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.InterfaceUrls.loansUrl;
import static api.support.http.InterfaceUrls.overrideRenewalByBarcodeUrl;
import static api.support.http.InterfaceUrls.renewByBarcodeUrl;
import static api.support.http.InterfaceUrls.renewByIdUrl;
import static api.support.http.Limit.maximumLimit;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static java.net.HttpURLConnection.HTTP_OK;

import java.net.URL;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import api.support.MultipleJsonRecords;
import api.support.RestAssuredClient;
import api.support.builders.LoanBuilder;
import api.support.builders.OverrideRenewalByBarcodeRequestBuilder;
import api.support.builders.RenewByBarcodeRequestBuilder;
import api.support.builders.RenewByIdRequestBuilder;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
import api.support.http.Limit;
import api.support.http.Offset;
import io.vertx.core.json.JsonObject;

public class LoansFixture {
  private final RestAssuredClient restAssuredClient;

  public LoansFixture() {
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public IndividualResource createLoan(IndividualResource item, IndividualResource to) {
    DateTime loanDate = DateTime.now();

    return createLoan(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId())
      .withLoanDate(loanDate)
      .withDueDate(loanDate.plusWeeks(3)));
  }

  public IndividualResource createLoan(IndividualResource item, IndividualResource to,
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

  public Response attemptToCreateLoan(IndividualResource item, IndividualResource to) {
    return attemptToCreateLoan(item, to, UNPROCESSABLE_ENTITY);
  }

  public Response attemptToCreateLoan(IndividualResource item, IndividualResource to,
    int expectedStatusCode) {

    return attemptToCreateLoan(new LoanBuilder()
      .open()
      .withItemId(item.getId())
      .withUserId(to.getId()), expectedStatusCode);
  }

  public Response attemptToCreateLoan(LoanBuilder loanBuilder, int expectedStatusCode) {
    return restAssuredClient.post(loanBuilder.create(), loansUrl(),
      expectedStatusCode, "post-loan");
  }

  public void createLoanAtSpecificLocation(UUID loanId, LoanBuilder loanBuilder) {
    restAssuredClient.put(loanBuilder.create(), urlForLoan(loanId), 204,
      "create-loan-at-specific-location");
  }

  public Response attemptToCreateLoanAtSpecificLocation(UUID loanId, LoanBuilder loanBuilder) {
    return restAssuredClient.put(loanBuilder.create(), urlForLoan(loanId),
      "attempt-to-create-loan-at-specific-location");
  }

  public void replaceLoan(UUID loanId, JsonObject representation) {
    restAssuredClient.put(representation, urlForLoan(loanId), 204, "replace-loan");
  }

  public Response attemptToReplaceLoan(UUID loanId, JsonObject representation) {
    return restAssuredClient.put(representation, urlForLoan(loanId), "replace-loan");
  }

  public IndividualResource renewLoan(IndividualResource item, IndividualResource user) {
    JsonObject request = new RenewByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(restAssuredClient.post(request, renewByBarcodeUrl(),
      200, "renewal-by-barcode-request"));
  }

  public IndividualResource overrideRenewalByBarcode(OverrideRenewalByBarcodeRequestBuilder request) {
    return new IndividualResource(restAssuredClient.post(request.create(),
      overrideRenewalByBarcodeUrl(), 200, "override-renewal-by-barcode-request"));
  }

  public IndividualResource overrideRenewalByBarcode(IndividualResource item,
      IndividualResource user, String comment, String dueDate) {

    return overrideRenewalByBarcode(new OverrideRenewalByBarcodeRequestBuilder()
        .forItem(item)
        .forUser(user)
        .withComment(comment)
        .withDueDate(dueDate));
  }

  public IndividualResource renewLoanById(IndividualResource item, IndividualResource user) {
    JsonObject request = new RenewByIdRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return new IndividualResource(restAssuredClient.post(request, renewByIdUrl(),
      200, "renewal-by-id-request"));
  }

  public Response attemptRenewal(IndividualResource item, IndividualResource user) {
    return attemptRenewal(422, item, user);
  }

  public Response attemptRenewal(int expectedStatusCode, IndividualResource item,
      IndividualResource user) {

    JsonObject request = new RenewByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return restAssuredClient.post(request, renewByBarcodeUrl(),
      expectedStatusCode, "renewal-by-barcode-request");
  }

  public Response attemptOverride(IndividualResource item, IndividualResource user,
    String comment, String dueDate) {

    return attemptOverride(422, item, user, comment, dueDate);
  }

  public Response attemptOverride(int expectedStatusCode, IndividualResource item,
    IndividualResource user, String comment, String dueDate) {

    JsonObject request = new OverrideRenewalByBarcodeRequestBuilder()
      .forItem(item)
      .forUser(user)
      .withComment(comment)
      .withDueDate(dueDate)
      .create();

    return restAssuredClient.post(request, overrideRenewalByBarcodeUrl(),
      expectedStatusCode, "override-renewal-by-barcode-request");
  }

  public Response attemptRenewalById(IndividualResource item, IndividualResource user) {
    JsonObject request = new RenewByIdRequestBuilder()
      .forItem(item)
      .forUser(user)
      .create();

    return restAssuredClient.post(request, renewByIdUrl(), 422, "renewal-by-id-request");
  }

  public IndividualResource getLoanById(UUID id) {
    return new IndividualResource(restAssuredClient.get(urlForLoan(id), 200, "get-loan-by-id"));
  }

  public Response getLoanByLocation(IndividualResource response) {
    return restAssuredClient.get(circulationModuleUrl(response.getLocation()),
      HTTP_OK, "get-created-loan");
  }

  public MultipleJsonRecords getLoans() {
    return getLoans(noQuery());
  }

  public MultipleJsonRecords getLoans(Limit limit) {
    return getLoans(noQuery(), limit, noOffset());
  }

  public MultipleJsonRecords getLoans(Limit limit, Offset offset) {
    return getLoans(noQuery(), limit, offset);
  }

  public MultipleJsonRecords getLoans(CqlQuery query) {
    return getLoans(query, noLimit(), noOffset());
  }

  public MultipleJsonRecords getLoans(CqlQuery query, Limit limit, Offset offset) {
    return multipleRecordsFrom(
      restAssuredClient.get(loansUrl(), query, limit, offset, 200,
      "get-loans"), "loans");
  }

  public MultipleJsonRecords getAllLoans() {
    return getLoans(noQuery(), maximumLimit(), noOffset());
  }

  public void deleteLoan(UUID loanId) {
    restAssuredClient.delete(urlForLoan(loanId), 204, "delete-loan");
  }

  private URL urlForLoan(UUID id) {
    return loansUrl(String.format("/%s", id));
  }
}
