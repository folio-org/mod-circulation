package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.checkOutByBarcodeUrl;
import static api.support.http.InterfaceUrls.overrideCheckOutByBarcodeUrl;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import api.support.RestAssuredClient;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.OverrideCheckOutByBarcodeRequestBuilder;
import api.support.http.CheckOutResource;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutFixture {
  private final UsersFixture usersFixture;
  private final ServicePointsFixture servicePointsFixture;
  private final RestAssuredClient restAssuredClient;

  public CheckOutFixture(UsersFixture usersFixture, ServicePointsFixture servicePointsFixture) {
    this.usersFixture = usersFixture;
    this.servicePointsFixture = servicePointsFixture;
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public CheckOutResource checkOutByBarcode(IndividualResource item) {
    return checkOutByBarcode(item, usersFixture.jessica());
  }

  public CheckOutResource checkOutByBarcode(IndividualResource item, IndividualResource to) {
    return checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .at(defaultServicePoint()));
  }

  public CheckOutResource checkOutByBarcode(IndividualResource item,
    IndividualResource to, DateTime loanDate) {

    return checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .on(loanDate)
      .at(defaultServicePoint()));
  }

  public CheckOutResource checkOutByBarcode(CheckOutByBarcodeRequestBuilder builder) {
    JsonObject request = builder.create();

    return new CheckOutResource(restAssuredClient.post(request, checkOutByBarcodeUrl(), 201,
      "check-out-by-barcode-request"));
  }

  public Response attemptCheckOutByBarcode(IndividualResource item, IndividualResource to) {
    return attemptCheckOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .at(UUID.randomUUID()));
  }

  public Response attemptCheckOutByBarcode(CheckOutByBarcodeRequestBuilder builder) {
    return attemptCheckOutByBarcode(422, builder);
  }

  public Response attemptCheckOutByBarcode(int expectedStatusCode,
    CheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return restAssuredClient.post(request, checkOutByBarcodeUrl(),
      expectedStatusCode, "check-out-by-barcode-request");
  }

  public IndividualResource overrideCheckOutByBarcode(
    OverrideCheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return new IndividualResource(
      restAssuredClient.post(request, overrideCheckOutByBarcodeUrl(), 201,
        "override-check-out-by-barcode-request"));
  }

  public Response attemptOverrideCheckOutByBarcode(
    OverrideCheckOutByBarcodeRequestBuilder builder) {

    return attemptOverrideCheckOutByBarcode(422, builder);
  }

  public Response attemptOverrideCheckOutByBarcode(int expectedStatusCode,
    OverrideCheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return restAssuredClient.post(request, overrideCheckOutByBarcodeUrl(),
      expectedStatusCode, "override-check-out-by-barcode-request");
  }

  private IndividualResource defaultServicePoint() {
    return servicePointsFixture.cd1();
  }
}
