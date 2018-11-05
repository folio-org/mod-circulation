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
  private final String checkoutServicePointId;

  public CheckOutByBarcodeRequestBuilder() {
    this(null, null, null, null, null);
  }

  private CheckOutByBarcodeRequestBuilder(String itemBarcode, String userBarcode, String proxyBarcode,
      DateTime loanDate, String checkoutServicePointId) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.proxyBarcode = proxyBarcode;
    this.loanDate = loanDate;
    this.checkoutServicePointId = checkoutServicePointId;
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
    return new CheckOutByBarcodeRequestBuilder(getBarcode(item), this.userBarcode, this.proxyBarcode, this.loanDate,
        null);
  }

  public CheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeRequestBuilder(this.itemBarcode, getBarcode(loanee), this.proxyBarcode, this.loanDate,
        null);
  }

  public CheckOutByBarcodeRequestBuilder on(DateTime loanDate) {
    return new CheckOutByBarcodeRequestBuilder(this.itemBarcode, this.userBarcode, this.proxyBarcode, loanDate, null);
  }

  public CheckOutByBarcodeRequestBuilder proxiedBy(IndividualResource proxy) {
    return new CheckOutByBarcodeRequestBuilder(this.itemBarcode, this.userBarcode, getBarcode(proxy), this.loanDate,
        null);
  }

  public CheckOutByBarcodeRequestBuilder at(String checkoutServicePointId) {
    return new CheckOutByBarcodeRequestBuilder(this.itemBarcode, this.userBarcode, this.proxyBarcode, this.loanDate,
        checkoutServicePointId);
  }

  public CheckOutByBarcodeRequestBuilder at(UUID checkoutServicePointId) {
    return new CheckOutByBarcodeRequestBuilder(this.itemBarcode, this.userBarcode, this.proxyBarcode, this.loanDate,
        checkoutServicePointId.toString());
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
