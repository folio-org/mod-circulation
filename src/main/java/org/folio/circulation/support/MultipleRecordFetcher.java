package org.folio.circulation.support;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;

import io.vertx.core.json.JsonObject;

public class MultipleRecordFetcher {
  public static CompletableFuture<Result<MultipleRecords<JsonObject>>> findByIds(
    List<String> idList, Function<JsonObject, JsonObject> recordMapper,
    String recordsPropertyName, CollectionResourceClient client) {

    String locationsQuery = CqlHelper.multipleRecordsCqlQuery(idList);

    return client.getMany(locationsQuery, idList.size(), 0)
      .thenApply(response -> MultipleRecords.from(response, recordMapper,
        recordsPropertyName));
  }
}
