package api.support.fixtures;

import static api.APITestSuite.createReferenceRecord;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.http.ResourceClient;

public class MaterialTypesFixture {
  public static UUID bookMaterialTypeId;
  public static UUID videoRecordingMaterialTypeId;

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

    videoRecordingMaterialTypeId = createReferenceRecord(materialTypesClient,
      "Video Recording");

  }

  public void book()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    bookMaterialTypeId = createReferenceRecord(materialTypesClient, "Book");
  }
}
