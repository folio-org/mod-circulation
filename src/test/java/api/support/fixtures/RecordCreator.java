package api.support.fixtures;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.Builder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

class RecordCreator {
  private final ResourceClient client;
  private final Map<String, IndividualResource> identityMap;
  private final Set<UUID> createdRecordIds;

  RecordCreator(ResourceClient client) {
    this.client = client;
    this.identityMap = new HashMap<>();
    this.createdRecordIds = new HashSet<>();
  }

  IndividualResource create(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    return create(builder.create());
  }

  private IndividualResource create(JsonObject record)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource createdRecord = client.create(record);

    createdRecordIds.add(createdRecord.getId());

    return createdRecord;
  }

  void cleanUp()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    for (UUID userId : createdRecordIds) {
      client.delete(userId);
    }

    createdRecordIds.clear();
  }

  IndividualResource createIfAbsent(String key, JsonObject record)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    if(needsCreating(key)) {
      final IndividualResource user = create(record);

      identityMap.put(key, user);
    }

    return identityMap.get(key);
  }

  private boolean needsCreating(String key) {
    return !identityMap.containsKey(key);
  }
}
