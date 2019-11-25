package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.builders.UserManualBlockBuilder;
import api.support.http.ResourceClient;

import org.folio.circulation.support.http.client.IndividualResource;

public class UserManualBlocksFixture {
  private final RecordCreator userManualBlocksRecordCreator;

  public UserManualBlocksFixture(ResourceClient manualBlocksClient) {
    userManualBlocksRecordCreator = new RecordCreator(manualBlocksClient,
      manualBlock -> getProperty(manualBlock, "id"));
  }

  public IndividualResource create(UserManualBlockBuilder userManualBlockBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userManualBlocksRecordCreator.createIfAbsent(userManualBlockBuilder.create());
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    userManualBlocksRecordCreator.cleanUp();
  }
}
