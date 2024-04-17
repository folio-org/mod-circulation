package api.support.builders;

import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.time.ZonedDateTime;
import java.util.UUID;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyBarcode;
  private final ZonedDateTime loanDate;
  private final String servicePointId;
  private final JsonObject overrideBlocks;

  public CheckOutByBarcodeRequestBuilder() {
    this(null, null, null, null, null, null);
  }

  private CheckOutByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode,
    String proxyBarcode,
    ZonedDateTime loanDate,
    String servicePointId,
    JsonObject overrideBlocks) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyBarcode = proxyBarcode;
    this.loanDate = loanDate;
    this.servicePointId = servicePointId;
    this.overrideBlocks = overrideBlocks;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "proxyUserBarcode", this.proxyBarcode);
    put(request, "loanDate", formatDateTimeOptional(this.loanDate));
    put(request, "servicePointId", this.servicePointId);
    put(request, "overrideBlocks", this.overrideBlocks);

    return request;
  }

  public CheckOutByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new CheckOutByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder to(String userBarcode) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder on(ZonedDateTime loanDate) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      loanDate,
      this.servicePointId,
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder proxiedBy(IndividualResource proxy) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      getBarcode(proxy),
      this.loanDate,
      this.servicePointId,
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder at(String checkoutServicePointId) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId,
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder at(IndividualResource checkoutServicePoint) {
    return at(checkoutServicePoint.getId());
  }

  public CheckOutByBarcodeRequestBuilder at(UUID checkoutServicePointId) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId.toString(),
      this.overrideBlocks);
  }

  public CheckOutByBarcodeRequestBuilder withOverrideBlocks(JsonObject overrideBlocks) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      overrideBlocks);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
