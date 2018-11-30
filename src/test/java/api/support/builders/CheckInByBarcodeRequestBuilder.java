package api.support.builders;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final DateTime checkInDate;
  private final String servicePointId;

  public CheckInByBarcodeRequestBuilder() {
    this(null, DateTime.now(DateTimeZone.UTC), null);
  }

  private CheckInByBarcodeRequestBuilder(
    String itemBarcode,
    DateTime checkInDate,
    String servicePointId) {

    this.itemBarcode = itemBarcode;
    this.checkInDate = checkInDate;
    this.servicePointId = servicePointId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "checkInDate", this.checkInDate);
    put(request, "servicePointId", this.servicePointId);

    return request;
  }

  public CheckInByBarcodeRequestBuilder forItem(IndividualResource item) {
    return withItemBarcode(getBarcode(item));
  }

  public CheckInByBarcodeRequestBuilder withItemBarcode(String itemBarcode) {
    return new CheckInByBarcodeRequestBuilder(
      itemBarcode,
      this.checkInDate,
      this.servicePointId);
  }

  public CheckInByBarcodeRequestBuilder noItem() {
    return withItemBarcode(null);
  }

  public CheckInByBarcodeRequestBuilder to() {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      this.servicePointId);
  }

  public CheckInByBarcodeRequestBuilder on(DateTime checkInDate) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      checkInDate,
      this.servicePointId);
  }

  public CheckInByBarcodeRequestBuilder onNoOccasion() {
    return on(null);
  }

  public CheckInByBarcodeRequestBuilder at(String servicePointId) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      servicePointId);
  }

  public CheckInByBarcodeRequestBuilder at(UUID servicePointId) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      servicePointId.toString());
  }

  public CheckInByBarcodeRequestBuilder atNoServicePoint() {
    return at((String)null);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
