package api.support.builders;

import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLVED_BY;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final DateTime checkInDate;
  private final String servicePointId;
  private final String claimedReturnedResolvedBy;

  public CheckInByBarcodeRequestBuilder() {
    this(null, DateTime.now(DateTimeZone.UTC), null, null);
  }

  private CheckInByBarcodeRequestBuilder(
    String itemBarcode,
    DateTime checkInDate,
    String servicePointId,
    String claimedReturnedResolvedBy) {

    this.itemBarcode = itemBarcode;
    this.checkInDate = checkInDate;
    this.servicePointId = servicePointId;
    this.claimedReturnedResolvedBy = claimedReturnedResolvedBy;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "checkInDate", this.checkInDate);
    put(request, "servicePointId", this.servicePointId);
    put(request, CLAIMED_RETURNED_RESOLVED_BY, this.claimedReturnedResolvedBy);

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
      this.claimedReturnedResolvedBy);
  }

  public CheckInByBarcodeRequestBuilder noItem() {
    return withItemBarcode(null);
  }

  public CheckInByBarcodeRequestBuilder to() {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      this.servicePointId,
      this.claimedReturnedResolvedBy);
  }

  public CheckInByBarcodeRequestBuilder on(DateTime checkInDate) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      checkInDate,
      this.servicePointId,
      this.claimedReturnedResolvedBy);
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
      this.claimedReturnedResolvedBy);
  }


  public CheckInByBarcodeRequestBuilder atNoServicePoint() {
    return at((String)null);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }

  public CheckInByBarcodeRequestBuilder claimedReturnedResolvedBy(String resolvedBy) {
    return new CheckInByBarcodeRequestBuilder(
      this.itemBarcode,
      this.checkInDate,
      this.servicePointId,
      resolvedBy);
  }
}
