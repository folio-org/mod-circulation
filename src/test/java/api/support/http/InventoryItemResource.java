package api.support.http;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

public class InventoryItemResource extends IndividualResource {
  private final IndividualResource holdingsRecord;
  private final IndividualResource instance;

  public InventoryItemResource(
    IndividualResource item,
    IndividualResource holdingsRecord,
    IndividualResource instance) {

    super(item.getResponse());
    this.holdingsRecord = holdingsRecord;
    this.instance = instance;
  }

  public UUID getHoldingsRecordId() {
    return holdingsRecord.getId();
  }

  public UUID getInstanceId() {
    return instance.getId();
  }

  public IndividualResource getInstance() {
    return instance;
  }

  public IndividualResource getHoldingsRecord() {
    return holdingsRecord;
  }
}
