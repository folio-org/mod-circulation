package api.support.builders;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class OverrideRenewalByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final String comment;
  private final String dueDate;
  private final String servicePointId;

  public OverrideRenewalByBarcodeRequestBuilder() {
    this(null, null, null, null, null);
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "comment", this.comment);
    put(request, "dueDate", this.dueDate);
    put(request, "servicePointId", this.servicePointId);

    return request;
  }

  public OverrideRenewalByBarcodeRequestBuilder forItem(IndividualResource item) {
    return withItemBarcode(getBarcode(item));
  }

  public OverrideRenewalByBarcodeRequestBuilder forUser(IndividualResource loanee) {
    return withUserBarcode(getBarcode(loanee));
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
