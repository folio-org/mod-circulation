package api.support.fixtures;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class IdentifierTypesFixture {
  private final RecordCreator identifierTypesCreator;

  public IdentifierTypesFixture() {
    final ResourceClient identifiersResource = ResourceClient
      .forIdentifierTypes();

    this.identifierTypesCreator = new RecordCreator(
      identifiersResource, this::getIdentifierKey);
  }

  public IndividualResource isbn() {
    final JsonObject isbn = forFolioSource("ISBN");

    return identifierTypesCreator.createIfAbsent(isbn);
  }

  private JsonObject forFolioSource(String name) {
    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", name)
      .put("source", "folio");
  }

  private String getIdentifierKey(JsonObject identifier) {
    return identifier.getString("name") + identifier.getString("source");
  }
}
