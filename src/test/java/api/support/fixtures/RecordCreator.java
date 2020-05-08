package api.support.fixtures;

import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

import api.support.builders.Builder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

class RecordCreator {
  private final ResourceClient client;
  private final Function<JsonObject, String> identityMapKey;

  RecordCreator(
    ResourceClient client,
    Function<JsonObject, String> identityMapKey) {

    this.client = client;
    this.identityMapKey = identityMapKey;
  }

  private IndividualResource create(JsonObject record) {
    return client.create(record);
  }

  public void cleanUp() {
    client.deleteAllIndividually();
  }

  IndividualResource createIfAbsent(Builder recordBuilder) {

    return createIfAbsent(recordBuilder.create());
  }

  IndividualResource createIfAbsent(JsonObject record) {

    return createIfAbsent(identityMapKey.apply(record), record);
  }

  private IndividualResource createIfAbsent(String key, JsonObject record) {
    return needsCreating(key) ? create(record) : getExistingRecord(key);
  }

  private boolean needsCreating(String key) {
    return getExistingRecord(key) == null;
  }

  public void delete(IndividualResource record) {
    client.delete(record.getId());
  }

  public IndividualResource getExistingRecord(String name){
     return client.getAll().stream()
       .filter(json -> identityMapKey.apply(json).equals(name))
       .findFirst()
       .map(this::wrapJsonToIndividualResource)
       .orElse(null);
  }

  private IndividualResource wrapJsonToIndividualResource(JsonObject json) {
    return new IndividualResource(new Response(201, json.toString(), "application/json"));
  }
}
