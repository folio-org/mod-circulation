package api.support.fixtures;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;

import api.support.builders.Builder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

public class RecordCreator {
  private final ResourceClient client;
  private final Function<JsonObject, String> identityMapKey;
  @Getter
  private final Map<String, IndividualResource> identityMap = new HashMap<>();

  RecordCreator(
    ResourceClient client,
    Function<JsonObject, String> identityMapKey) {

    this.client = client;
    this.identityMapKey = identityMapKey;
  }

  private IndividualResource create(JsonObject record) {
    final IndividualResource created = client.create(record);

    identityMap.put(identityMapKey.apply(record), created);

    return created;
  }

  public void cleanUp() {
    client.deleteAllIndividually();
    identityMap.clear();
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

    identityMap.values()
      .removeIf(value -> value.getId().equals(record.getId()));
  }

  public IndividualResource getExistingRecord(String name){
    if (identityMap.containsKey(name)) {
      return identityMap.get(name);
    }

    reloadIdentityMap();

    return identityMap.get(name);
  }

  private void reloadIdentityMap() {
    client.getAll().forEach(record -> identityMap.put(
      identityMapKey.apply(record), wrapJsonToIndividualResource(record)));
  }

  private IndividualResource wrapJsonToIndividualResource(JsonObject json) {
    return new IndividualResource(new Response(201, json.toString(), "application/json"));
  }
}
