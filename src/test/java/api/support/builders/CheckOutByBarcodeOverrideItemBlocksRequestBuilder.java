package api.support.builders;

import java.util.UUID;

import org.joda.time.DateTime;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeOverrideItemBlocksRequestBuilder extends JsonBuilder implements Builder {

  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyBarcode;
  private final DateTime loanDate;
  private final String servicePointId;
  private final String comment;

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
    String itemBarcode, String userBarcode, String proxyBarcode,
    DateTime loanDate, String servicePointId, String comment) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyBarcode = proxyBarcode;
    this.loanDate = loanDate;
    this.servicePointId = servicePointId;
    this.comment = comment;
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder() {
    this(null, null, null, null, null, null);
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "proxyUserBarcode", this.proxyBarcode);
    put(request, "loanDate", this.loanDate);
    put(request, "servicePointId", this.servicePointId);
    put(request, "comment", this.comment);

    return request;
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder forItem(IndividualResource item) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.comment);
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.comment);
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder on(DateTime loanDate) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      loanDate,
      this.servicePointId,
      this.comment);
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder proxiedBy(IndividualResource proxy) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      getBarcode(proxy),
      this.loanDate,
      this.servicePointId,
      this.comment);
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder at(String checkoutServicePointId) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId,
      this.comment);
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder at(IndividualResource checkoutServicePoint) {
    return at(checkoutServicePoint.getId());
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder at(UUID checkoutServicePointId) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId.toString(),
      this.comment);
  }

  public CheckOutByBarcodeOverrideItemBlocksRequestBuilder withComment(String comment) {
    return new CheckOutByBarcodeOverrideItemBlocksRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      comment);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
