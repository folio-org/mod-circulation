package api.support.fixtures;

import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import api.support.builders.UserManualBlockBuilder;
import api.support.http.ResourceClient;

import api.support.http.IndividualResource;

class UserManualBlocksFixture {
  private final RecordCreator userManualBlocksRecordCreator;

  public UserManualBlocksFixture(ResourceClient manualBlocksClient) {
    userManualBlocksRecordCreator = new RecordCreator(manualBlocksClient,
      manualBlock -> getProperty(manualBlock, "id"));
  }

  public IndividualResource create(UserManualBlockBuilder userManualBlockBuilder) {

    return userManualBlocksRecordCreator.createIfAbsent(userManualBlockBuilder.create());
  }

  public void cleanUp() {

    userManualBlocksRecordCreator.cleanUp();
  }

  public void createRequestsManualPatronBlockForUser(UUID requesterId) {
    create(getManualBlockBuilder()
      .withRequests(true)
      .withExpirationDate(getClockManager().getDateTime().plusYears(1))
      .withUserId(requesterId.toString()));
  }

  public void createRenewalsManualPatronBlockForUser(UUID requesterId) {
    create(getManualBlockBuilder()
      .withRenewals(true)
      .withExpirationDate(getClockManager().getDateTime().plusYears(1))
      .withUserId(requesterId.toString()));
  }

  public void createBorrowingManualPatronBlockForUser(UUID requesterId) {
    create(getManualBlockBuilder()
      .withBorrowing(true)
      .withExpirationDate(getClockManager().getDateTime().plusYears(1))
      .withUserId(requesterId.toString()));
  }

  private UserManualBlockBuilder getManualBlockBuilder() {
    return new UserManualBlockBuilder()
      .withType("Manual")
      .withDesc("Display description")
      .withStaffInformation("Staff information")
      .withPatronMessage("Patron message")
      .withId(UUID.randomUUID());
  }
}
