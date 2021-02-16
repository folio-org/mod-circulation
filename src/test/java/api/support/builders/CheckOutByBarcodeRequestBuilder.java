package api.support.builders;

import java.util.UUID;

import org.folio.circulation.domain.override.BlockOverrides;
import org.joda.time.DateTime;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyBarcode;
  private final DateTime loanDate;
  private final String servicePointId;
  private final BlockOverrides blockOverrides;

  public CheckOutByBarcodeRequestBuilder() {
    this(null, null, null, null, null, null);
  }

  private CheckOutByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode,
    String proxyBarcode,
    DateTime loanDate,
    String servicePointId,
    BlockOverrides blockOverrides) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyBarcode = proxyBarcode;
    this.loanDate = loanDate;
    this.servicePointId = servicePointId;
    this.blockOverrides = blockOverrides;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "proxyUserBarcode", this.proxyBarcode);
    put(request, "loanDate", this.loanDate);
    put(request, "servicePointId", this.servicePointId);
    if (blockOverrides != null) {
      JsonObject overrideBlocksJson = new JsonObject();
      if (blockOverrides.getItemNotLoanableBlockOverride() != null
        && blockOverrides.getItemNotLoanableBlockOverride().isRequested()) {

        JsonObject itemNotLoanableBlockJson = new JsonObject();
        put(itemNotLoanableBlockJson, "dueDate",
          blockOverrides.getItemNotLoanableBlockOverride().getDueDate());
        put(overrideBlocksJson, "itemNotLoanableBlock", itemNotLoanableBlockJson);
      }
      if (blockOverrides.getPatronBlockOverride() != null
        && blockOverrides.getPatronBlockOverride().isRequested()) {

        put(overrideBlocksJson, "patronBlock", new JsonObject());
      }
      if (blockOverrides.getItemLimitBlockOverride() != null
        && blockOverrides.getItemLimitBlockOverride().isRequested()) {

        put(overrideBlocksJson, "itemLimitBlock", new JsonObject());
      }
      put(overrideBlocksJson, "comment", blockOverrides.getComment());
      put(request, "overrideBlocks", overrideBlocksJson);
    }

    return request;
  }

  public CheckOutByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new CheckOutByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.blockOverrides);
  }

  public CheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.blockOverrides);
  }

  public CheckOutByBarcodeRequestBuilder on(DateTime loanDate) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      loanDate,
      this.servicePointId,
      this.blockOverrides);
  }

  public CheckOutByBarcodeRequestBuilder proxiedBy(IndividualResource proxy) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      getBarcode(proxy),
      this.loanDate,
      this.servicePointId,
      this.blockOverrides);
  }

  public CheckOutByBarcodeRequestBuilder at(String checkoutServicePointId) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId,
      this.blockOverrides);
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
      this.blockOverrides);
  }

  public CheckOutByBarcodeRequestBuilder withOverrideBlocks(BlockOverrides blockOverrides) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      blockOverrides);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
