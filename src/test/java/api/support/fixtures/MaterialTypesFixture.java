package api.support.fixtures;

import static api.APITestSuite.createReferenceRecord;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.http.ResourceClient;

public class MaterialTypesFixture {
  private UUID bookMaterialTypeId;
  private UUID videoRecordingMaterialTypeId;

  private final ResourceClient materialTypesClient;

  public MaterialTypesFixture(ResourceClient materialTypesClient) {
    this.materialTypesClient = materialTypesClient;
  }

  public UUID videoRecording()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(videoRecordingMaterialTypeId == null) {
      videoRecordingMaterialTypeId = createReferenceRecord(materialTypesClient,
          "Video Recording");
    }

    return videoRecordingMaterialTypeId;
  }

  public UUID book()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(bookMaterialTypeId == null) {
      bookMaterialTypeId = createReferenceRecord(materialTypesClient, "Book");
    }

    return bookMaterialTypeId;
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    if(bookMaterialTypeId != null) {
      materialTypesClient.delete(bookMaterialTypeId);
      bookMaterialTypeId = null;
    }

    if(videoRecordingMaterialTypeId != null) {
      materialTypesClient.delete(videoRecordingMaterialTypeId);
      videoRecordingMaterialTypeId = null;
    }
  }
}
