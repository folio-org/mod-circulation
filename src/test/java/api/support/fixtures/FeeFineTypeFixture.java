package api.support.fixtures;

import java.util.UUID;

import api.support.http.IndividualResource;

import api.support.builders.FeeFineBuilder;
import api.support.http.ResourceClient;

public final class FeeFineTypeFixture extends RecordCreator {
  public FeeFineTypeFixture() {
    super(ResourceClient.forFeeFines(), json -> json.getString("feeFineType"));
  }

  public IndividualResource lostItemFee() {
    return createIfAbsent(new FeeFineBuilder()
      .withFeeFineType("Lost item fee")
      .withAutomatic(true));
  }

  public IndividualResource lostItemActualCostFee() {
    return createIfAbsent(new FeeFineBuilder()
      .withFeeFineType("Lost item fee (actual cost)")
      .withAutomatic(true));
  }

  public IndividualResource lostItemProcessingFee() {
    return createIfAbsent(new FeeFineBuilder()
      .withFeeFineType("Lost item processing fee")
      .withAutomatic(true));
  }

  public IndividualResource lostItemActualCostFee() {

    return createIfAbsent(new FeeFineBuilder()
      .withFeeFineType("Lost item fee (actual cost)")
      .withAutomatic(true));
  }

  public IndividualResource overdueFine() {
    return createIfAbsent(new FeeFineBuilder()
      .withFeeFineType("Overdue fine")
      .withAutomatic(true));
  }

  public IndividualResource overdueFine(UUID ownerId) {
    return createIfAbsent(new FeeFineBuilder()
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true));
  }
}
