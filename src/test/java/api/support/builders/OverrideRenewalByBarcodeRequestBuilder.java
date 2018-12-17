package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

public class OverrideRenewalByBarcodeRequestBuilder extends JsonBuilder implements Builder {

  private final String itemBarcode;
  private final String userBarcode;
  private final String comment;
  private final String dueDate;

  public OverrideRenewalByBarcodeRequestBuilder() {
    this(null, null, null, null);
  }

  private OverrideRenewalByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode, String comment, String dueDate) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.comment = comment;
    this.dueDate = dueDate;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "comment", this.comment);
    put(request, "dueDate", this.dueDate);

    return request;
  }

  public OverrideRenewalByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new OverrideRenewalByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.comment,
      this.dueDate);
  }

  public OverrideRenewalByBarcodeRequestBuilder forUser(IndividualResource loanee) {
    return new OverrideRenewalByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.comment,
      this.dueDate);
  }

  public OverrideRenewalByBarcodeRequestBuilder withComment(String comment) {
    return new OverrideRenewalByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      comment,
      this.dueDate);
  }

  public OverrideRenewalByBarcodeRequestBuilder withDueDate(String dueDate) {
    return new OverrideRenewalByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.comment,
      dueDate);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
