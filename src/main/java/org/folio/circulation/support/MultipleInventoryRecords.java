package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class MultipleInventoryRecords {
  private final Collection<JsonObject> items;
  private final Collection<JsonObject> holdings;
  private final Collection<JsonObject> instances;

  MultipleInventoryRecords(
    Collection<JsonObject> items,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances) {

    this.items = items;
    this.holdings = holdings;
    this.instances = instances;
  }

  public Collection<InventoryRecords> getRecords() {
    return items.stream()
      .map(record -> findRecordByItemId(record.getString("id")))
      .filter(record -> record.getItem() != null)
      .collect(Collectors.toList());
  }

  public InventoryRecords findRecordByItemId(String itemId) {
    final Optional<JsonObject> item = findItemById(itemId);

    if(!item.isPresent()) {
      return new InventoryRecords(null, null, null, null, null);
    }

    final Optional<JsonObject> holdingsRecord = findHoldingById(
      item.get().getString("holdingsRecordId"));

    if(!holdingsRecord.isPresent()) {
      return new InventoryRecords(item.get(), null, null, null, null);
    }

    final Optional<JsonObject> instance = findInstanceById(
      holdingsRecord.get().getString("instanceId"));

    return
      new InventoryRecords(item.get(), holdingsRecord.get(), instance.orElse(null),
        null, null);
  }

  public Optional<JsonObject> findHoldingById(String holdingsId) {
    return findById(holdingsId, holdings);
  }

  public Optional<JsonObject> findItemById(String itemId) {
    return findById(itemId, items);
  }

  private Optional<JsonObject> findInstanceById(String instanceId) {
    return findById(instanceId, instances);
  }

  private Optional<JsonObject> findById(
    String id,
    Collection<JsonObject> collection) {

    return collection.stream()
      .filter(item -> item.getString("id").equals(id))
      .findFirst();
  }
}
