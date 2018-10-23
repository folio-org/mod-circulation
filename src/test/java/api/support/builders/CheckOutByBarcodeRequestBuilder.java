package api.support.builders;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckOutByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final String proxyBarcode;
  private final DateTime loanDate;
	private final String checkoutServicePointId = UUID.randomUUID().toString();

  public CheckOutByBarcodeRequestBuilder() {
    this(null, null, null, null);
  }

  private CheckOutByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode,
    String proxyBarcode,
    DateTime loanDate) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyBarcode = proxyBarcode;
    this.loanDate = loanDate;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "proxyUserBarcode", this.proxyBarcode);
    put(request, "loanDate", this.loanDate);
		put(request, "checkoutServicePointId", this.checkoutServicePointId);

    return request;
  }

  public CheckOutByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new CheckOutByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.proxyBarcode,
      this.loanDate);
  }

  public CheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.proxyBarcode,
      this.loanDate);
  }

  public CheckOutByBarcodeRequestBuilder at(DateTime loanDate) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.proxyBarcode,
      loanDate);
  }

  public CheckOutByBarcodeRequestBuilder proxiedBy(IndividualResource proxy) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      getBarcode(proxy),
      this.loanDate);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
