package api.support.http;

import org.folio.circulation.support.http.client.IndividualResource;

public class InventoryItemResource extends IndividualResource {
  public InventoryItemResource(IndividualResource item) {
    super(item.getResponse());
  }
}
