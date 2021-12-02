package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.checkInByBarcodeUrl;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;

import api.support.CheckInByBarcodeResponse;
import api.support.RestAssuredClient;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonObject;

public class CheckInFixture {
  private final ServicePointsFixture servicePointsFixture;
  private final RestAssuredClient restAssuredClient;

  public CheckInFixture(ServicePointsFixture servicePointsFixture) {

    this.servicePointsFixture = servicePointsFixture;
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response attemptCheckInByBarcode(CheckInByBarcodeRequestBuilder builder) {

    return attemptCheckInByBarcode(builder, getOkapiHeadersFromContext());
  }

  public Response attemptCheckInByBarcode(CheckInByBarcodeRequestBuilder builder,
    OkapiHeaders okapiHeaders) {

    return restAssuredClient.post(builder.create(), checkInByBarcodeUrl(), okapiHeaders);
  }

  public CheckInByBarcodeResponse checkInByBarcode(CheckInByBarcodeRequestBuilder builder) {
    return new CheckInByBarcodeResponse(restAssuredClient.post(builder.create(),
      checkInByBarcodeUrl(), 200, "check-in-by-barcode-request"));
  }

  public CheckInByBarcodeResponse checkInByBarcode(IndividualResource item) {
    return checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .on(ClockUtil.getZonedDateTime())
      .at(defaultServicePoint()));
  }

  public CheckInByBarcodeResponse checkInByBarcode(IndividualResource item,
    ZonedDateTime checkInDate, UUID servicePointId) {

    return checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .on(checkInDate)
      .at(servicePointId));
  }

  public CheckInByBarcodeResponse checkInByBarcode(IndividualResource item, ZonedDateTime checkInDate) {
    return checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .on(checkInDate)
      .at(defaultServicePoint()));
  }

  public CheckInByBarcodeResponse checkInByBarcode(IndividualResource item,
    UUID servicePointId) {

    return checkInByBarcode(item, ClockUtil.getZonedDateTime(), servicePointId);
  }

  public void checkInByBarcode(IndividualResource item, ZonedDateTime checkInDate,
    UUID servicePointId, OkapiHeaders okapiHeaders) {

    final JsonObject representation = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .on(checkInDate)
      .at(servicePointId).create();

    restAssuredClient.post(representation, checkInByBarcodeUrl(), 200, okapiHeaders);
  }

  public Response attemptCheckInByBarcode(int expectedStatusCode, CheckInByBarcodeRequestBuilder builder) {
    return restAssuredClient.post(builder.create(), checkInByBarcodeUrl(),
      expectedStatusCode, "check-in-by-barcode-request");
  }

  private IndividualResource defaultServicePoint() {
    return servicePointsFixture.cd1();
  }
}
