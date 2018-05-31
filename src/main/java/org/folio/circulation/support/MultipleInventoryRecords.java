package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class MultipleInventoryRecords {
  private final Collection<InventoryRecords> records;

  private MultipleInventoryRecords(Collection<InventoryRecords> records) {
    this.records = records;
  }

  public static MultipleInventoryRecords from(
    Collection<JsonObject> items,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances) {

    return new MultipleInventoryRecords(items.stream()
      .map(item -> itemToInventoryRecords(item, holdings, instances))
      .collect(Collectors.toList()));
  }

  private static InventoryRecords itemToInventoryRecords(
    JsonObject item,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances) {

    final Optional<JsonObject> holdingsRecord = findById(
      item.getString("holdingsRecordId"), holdings);

    if(!holdingsRecord.isPresent()) {
      return new InventoryRecords(item, null, null, null, null);
    }

    final Optional<JsonObject> instance = findById(
      holdingsRecord.get().getString("instanceId"), instances);

    return
      new InventoryRecords(item, holdingsRecord.get(), instance.orElse(null),
        null, null);
  }

  public Collection<InventoryRecords> getRecords() {
    return records;
  }

  public InventoryRecords findRecordByItemId(String itemId) {
    return records.stream()
      .filter(record -> record.getItemId().equals(itemId))
      .findFirst()
      .orElseGet(() -> new InventoryRecords(null, null, null, null, null));
  }

  private static Optional<JsonObject> findById(
    String id,
    Collection<JsonObject> collection) {

    return collection.stream()
      .filter(item -> item.getString("id").equals(id))
      .findFirst();
  }
}
