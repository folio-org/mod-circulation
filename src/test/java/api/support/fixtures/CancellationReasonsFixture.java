package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import api.support.http.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class CancellationReasonsFixture {
  private final RecordCreator cancellationReasonsRecordCreator;

  public CancellationReasonsFixture(ResourceClient cancellationReasonsClient) {
    cancellationReasonsRecordCreator = new RecordCreator(cancellationReasonsClient,
      reason -> getProperty(reason, "name"));
  }

  public void cleanUp() {

    cancellationReasonsRecordCreator.cleanUp();
  }

  public IndividualResource courseReserves() {

    return createReason("Course Reserves", "Item needed for course reserves");
  }

  public IndividualResource itemNotAvailable() {

    return createReason("Item Not Available", "Item is no longer available", "Item is no longer available");
  }

  private IndividualResource createReason(String name, String description) {

    return createReason(name, description, null);
  }

  private IndividualResource createReason(String name, String description, String publicDescription) {

    final JsonObject reason = new JsonObject();

    write(reason, "name", name);
    write(reason, "description", description);
    write(reason, "publicDescription", publicDescription);

    return cancellationReasonsRecordCreator.createIfAbsent(reason);
  }
}
