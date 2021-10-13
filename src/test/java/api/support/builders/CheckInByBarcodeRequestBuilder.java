package api.support.builders;

import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final ZonedDateTime checkInDate;
  private final String servicePointId;
  private final String claimedReturnedResolution;

  public CheckInByBarcodeRequestBuilder() {
    this(null, ClockUtil.getZonedDateTime(), null, null);
  }

  private CheckInByBarcodeRequestBuilder(
    String itemBarcode,
    ZonedDateTime checkInDate,
    String servicePointId,
    String claimedReturnedResolution) {

    this.itemBarcode = itemBarcode;
    this.checkInDate = checkInDate;
    this.servicePointId = servicePointId;
    this.claimedReturnedResolution = claimedReturnedResolution;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "checkInDate", formatDateTimeOptional(this.checkInDate));
    put(request, "servicePointId", this.servicePointId);
    put(request, "claimedReturnedResolution", this.claimedReturnedResolution);

    return request;
  }

  public CheckInByBarcodeRequestBuilder forItem(IndividualResource item) {
    return withItemBarcode(getBarcode(item));
  }

  public CheckInByBarcodeRequestBuilder withItemBarcode(String itemBarcode) {
    return new CheckInByBarcodeRequestBuilder(
      itemBarcode,
      this.checkInDate,
      this.servicePointId,
      this.claimedReturnedResolution);
  }

  public CheckInByBarcodeRequestBuilder noItem() {
    return withItemBarcode(null);
  }

  public CheckInByBarcodeRequestBuilder to() {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      this.servicePointId,
      this.claimedReturnedResolution);
  }

  public CheckInByBarcodeRequestBuilder on(ZonedDateTime checkInDate) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      checkInDate,
      this.servicePointId,
      this.claimedReturnedResolution);
  }

  public CheckInByBarcodeRequestBuilder onNoOccasion() {
    return on(null);
  }

  public CheckInByBarcodeRequestBuilder at(IndividualResource servicePoint) {
    return at(servicePoint.getId());
  }

  public CheckInByBarcodeRequestBuilder at(UUID servicePointId) {
    return at(servicePointId.toString());
  }

  public CheckInByBarcodeRequestBuilder at(String servicePointId) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      servicePointId,
      this.claimedReturnedResolution);
  }


  public CheckInByBarcodeRequestBuilder atNoServicePoint() {
    return at((String)null);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }

  public CheckInByBarcodeRequestBuilder claimedReturnedResolution(String resolution) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      this.servicePointId,
      resolution);
  }
}
