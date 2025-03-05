package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.checkOutByBarcodeDryRunUrl;
import static api.support.http.InterfaceUrls.checkOutByBarcodeUrl;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.domain.representations.CheckOutByBarcodeDryRunRequest;
import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.http.CheckOutResource;
import api.support.http.IndividualResource;
import api.support.http.OkapiHeaders;
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
    IndividualResource to, ZonedDateTime loanDate) {

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

  public CheckOutResource checkOutByBarcode(CheckOutByBarcodeRequestBuilder builder,
    OkapiHeaders headers) {

    return new CheckOutResource(restAssuredClient.post(builder.create(),
      checkOutByBarcodeUrl(), 201, headers));
  }

  public CheckOutResource checkOutByBarcodeDryRun(CheckOutByBarcodeDryRunRequest request) {
    return new CheckOutResource(restAssuredClient.post(request,
      checkOutByBarcodeDryRunUrl(), 201, "check-out-by-barcode-dry-run-request"));
  }

  public CheckOutResource attemptCheckOutByBarcodeDryRun(CheckOutByBarcodeDryRunRequest request) {
    return new CheckOutResource(restAssuredClient.post(request,
      checkOutByBarcodeDryRunUrl(), 422, "check-out-by-barcode-dry-run-request"));
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

  public Response attemptCheckOutByBarcode(CheckOutByBarcodeRequestBuilder builder,
    OkapiHeaders headers) {

    return attemptCheckOutByBarcode(422, builder, headers);
  }

  public Response attemptCheckOutByBarcode(int expectedStatusCode,
    CheckOutByBarcodeRequestBuilder builder) {

    JsonObject request = builder.create();

    return restAssuredClient.post(request, checkOutByBarcodeUrl(),
      expectedStatusCode, "check-out-by-barcode-request");
  }

  public Response attemptCheckOutByBarcode(int expectedStatusCode,
    CheckOutByBarcodeRequestBuilder builder, OkapiHeaders headers) {

    return restAssuredClient.post(builder.create(), checkOutByBarcodeUrl(),
      expectedStatusCode, headers);
  }

  public IndividualResource checkOutByBarcodeResource(IndividualResource item, IndividualResource to) {
    return new CheckOutResource(restAssuredClient.post(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(to)
      .at(UUID.randomUUID()).create(), checkOutByBarcodeUrl(), "check-out-by-barcode-request"));
  }

  private IndividualResource defaultServicePoint() {
    return servicePointsFixture.cd1();
  }
}
