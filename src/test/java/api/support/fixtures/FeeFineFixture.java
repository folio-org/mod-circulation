package api.support.fixtures;

import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.FeeFineBuilder;
import api.support.http.ResourceClient;

public final class FeeFineFixture {
  private final RecordCreator recordCreator;

  public FeeFineFixture(ResourceClient client) {
    this.recordCreator = new RecordCreator(client,
      json -> json.getString("feeFineType"));
  }

  public IndividualResource lostItemFee() {
    return recordCreator.createIfAbsent(new FeeFineBuilder()
      .withFeeFineType(FeeFine.LOST_ITEM_FEE)
      .withAutomatic(true));
  }

  public IndividualResource lostItemProcessingFee() {
    return recordCreator.createIfAbsent(new FeeFineBuilder()
      .withFeeFineType(FeeFine.LOST_ITEM_PROCESSING_FEE)
      .withAutomatic(true));
  }

  public void cleanUp() {
    recordCreator.cleanUp();
  }
}
