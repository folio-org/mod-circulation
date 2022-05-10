package api.support.http;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;

import java.util.UUID;

public class ItemResource extends IndividualResource {
  private final IndividualResource holdingsRecord;
  private final IndividualResource instance;

  public ItemResource(IndividualResource item,
    IndividualResource holdingsRecord, IndividualResource instance) {

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

  public String getBarcode() {
    return response.getJson().getString("barcode");
  }

  public String getStatusName() {
    return getNestedStringProperty(response.getJson(), "status", "name");
  }
}
