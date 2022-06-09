package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import api.support.http.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class MaterialTypesFixture {
  private final RecordCreator materialTypeRecordCreator;

  public MaterialTypesFixture(ResourceClient materialTypesClient) {
    materialTypeRecordCreator = new RecordCreator(materialTypesClient,
      materialType -> getProperty(materialType, "name"));
  }

  public IndividualResource videoRecording() {

    final JsonObject videoRecording = materialType("Video Recording");

    return materialTypeRecordCreator.createIfAbsent(videoRecording);
  }

  public IndividualResource book() {

    final JsonObject book = materialType("Book");

    return materialTypeRecordCreator.createIfAbsent(book);
  }

  private JsonObject materialType(String name) {
    final JsonObject materialType = new JsonObject();

    write(materialType, "name", name);

    return materialType;
  }

  public void cleanUp() {

    materialTypeRecordCreator.cleanUp();
  }
}
