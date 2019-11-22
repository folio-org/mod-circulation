package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.builders.ManualBlockBuilder;
import api.support.http.ResourceClient;

import org.folio.circulation.support.http.client.IndividualResource;

public class ManualBlocksFixture {
  private final RecordCreator manualBlocksRecordCreator;

  public ManualBlocksFixture(ResourceClient manualBlocksClient) {
    manualBlocksRecordCreator = new RecordCreator(manualBlocksClient,
      manualBlock -> getProperty(manualBlock, "id"));
  }

  public IndividualResource create(ManualBlockBuilder manualBlockBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return manualBlocksRecordCreator.createIfAbsent(manualBlockBuilder.create());
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    manualBlocksRecordCreator.cleanUp();
  }
}
