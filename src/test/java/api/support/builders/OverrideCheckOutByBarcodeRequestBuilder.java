package api.support.builders;

import java.util.UUID;

import api.support.http.IndividualResource;

import org.folio.circulation.domain.representations.OverrideBlocks;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class OverrideCheckOutByBarcodeRequestBuilder extends JsonBuilder implements Builder {

  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyBarcode;
  private final DateTime loanDate;
  private final String servicePointId;
  private final DateTime dueDate;
  private final String comment;
  private final OverrideBlocks overrideBlocks;

  public OverrideCheckOutByBarcodeRequestBuilder(
    String itemBarcode, String userBarcode, String proxyBarcode,
    DateTime loanDate, String servicePointId, DateTime dueDate, String comment,
    OverrideBlocks overrideBlocks) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyBarcode = proxyBarcode;
    this.loanDate = loanDate;
    this.servicePointId = servicePointId;
    this.dueDate = dueDate;
    this.comment = comment;
    this.overrideBlocks = overrideBlocks;
  }

  public OverrideCheckOutByBarcodeRequestBuilder() {
    this(null, null, null, null, null, null, null, null);
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "proxyUserBarcode", this.proxyBarcode);
    put(request, "loanDate", this.loanDate);
    put(request, "servicePointId", this.servicePointId);
    put(request, "dueDate", this.dueDate);
    put(request, "comment", this.comment);
    if (overrideBlocks != null) {
      JsonObject overrideBlocksJson = new JsonObject();
      if (overrideBlocks.getItemNotLoanableBlock() != null) {
        JsonObject itemNotLoanableBlockJson = new JsonObject();
        put(itemNotLoanableBlockJson, "dueDate",
          overrideBlocks.getItemNotLoanableBlock().getDueDate());
        put(overrideBlocksJson, "itemNotLoanableBlock", itemNotLoanableBlockJson);
      }
      if (overrideBlocks.getPatronBlock() != null) {
        put(overrideBlocksJson, "patronBlock", new JsonObject());
      }
      if (overrideBlocks.getItemLimitBlock() != null) {
        put(overrideBlocksJson, "itemLimitBlock", new JsonObject());
      }
      put(request, "overrideBlocks", overrideBlocksJson);
    }

    return request;
  }

  public OverrideCheckOutByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder on(DateTime loanDate) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      loanDate,
      this.servicePointId,
      this.dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder proxiedBy(IndividualResource proxy) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      getBarcode(proxy),
      this.loanDate,
      this.servicePointId,
      this.dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder at(String checkoutServicePointId) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId,
      this.dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder at(IndividualResource checkoutServicePoint) {
    return at(checkoutServicePoint.getId());
  }

  public OverrideCheckOutByBarcodeRequestBuilder at(UUID checkoutServicePointId) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      checkoutServicePointId.toString(),
      this.dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder withDueDate(DateTime dueDate) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      dueDate,
      this.comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder withComment(String comment) {
    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.dueDate,
      comment,
      this.overrideBlocks);
  }

  public OverrideCheckOutByBarcodeRequestBuilder withOverrideBlocks(
    OverrideBlocks overrideBlocks) {

    return new OverrideCheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate,
      this.servicePointId,
      this.dueDate,
      this.comment,
      overrideBlocks);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
