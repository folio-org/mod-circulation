package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import api.support.builders.UserManualBlockBuilder;
import api.support.http.ResourceClient;

import api.support.http.IndividualResource;

public class UserManualBlocksFixture {
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
}
