package api.support.fixtures;

import static api.APITestSuite.createReferenceRecord;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.http.ResourceClient;

public class MaterialTypesFixture {
  private static UUID bookMaterialTypeId;
  private static UUID videoRecordingMaterialTypeId;

  private final ResourceClient materialTypesClient;

  public MaterialTypesFixture(ResourceClient materialTypesClient) {
    this.materialTypesClient = materialTypesClient;
  }

  public static UUID bookMaterialTypeId() {
    return bookMaterialTypeId;
  }

  public static UUID videoRecordingMaterialTypeId() {
    return videoRecordingMaterialTypeId;
  }

  public void videoRecording()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(videoRecordingMaterialTypeId == null) {
      videoRecordingMaterialTypeId = createReferenceRecord(materialTypesClient,
          "Video Recording");
    }
  }

  public void book()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(bookMaterialTypeId == null) {
      bookMaterialTypeId = createReferenceRecord(materialTypesClient, "Book");
    }
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    materialTypesClient.delete(bookMaterialTypeId);
    bookMaterialTypeId = null;

    materialTypesClient.delete(videoRecordingMaterialTypeId);
    videoRecordingMaterialTypeId = null;
  }
}
