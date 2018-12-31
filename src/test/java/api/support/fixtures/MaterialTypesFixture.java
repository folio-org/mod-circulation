package api.support.fixtures;

import static api.APITestSuite.createReferenceRecord;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.http.ResourceClient;

public class MaterialTypesFixture {
  private final ResourceClient materialTypesClient;

  public MaterialTypesFixture(ResourceClient materialTypesClient) {
    this.materialTypesClient = materialTypesClient;
  }

  public UUID videoRecording()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createReferenceRecord(materialTypesClient, "Video Recording");
  }

  public UUID book()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createReferenceRecord(materialTypesClient, "Book");
  }
}
