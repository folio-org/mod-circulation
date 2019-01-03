package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class MaterialTypesFixture {
  private final RecordCreator materialTypeRecordCreator;

  public MaterialTypesFixture(ResourceClient materialTypesClient) {
    materialTypeRecordCreator = new RecordCreator(materialTypesClient,
      materialType -> getProperty(materialType, "name"));
  }

  public UUID videoRecording()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final JsonObject videoRecording = materialType("Video Recording");

    return materialTypeRecordCreator.createIfAbsent(videoRecording).getId();
  }

  public UUID book()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final JsonObject book = materialType("Book");

    return materialTypeRecordCreator.createIfAbsent(book).getId();
  }

  private JsonObject materialType(String name) {
    final JsonObject book = new JsonObject();

    write(book, "name", name);

    return book;
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    materialTypeRecordCreator.cleanUp();
  }
}
