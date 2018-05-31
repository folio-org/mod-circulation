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
      .map(item -> findRecordByItemId(item.getString("id")))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private Optional<InventoryRecords> findRecordByItemId(String itemId) {
    final Optional<JsonObject> item = findItemById(itemId);

    if(!item.isPresent()) {
      return Optional.empty();
    }

    final Optional<JsonObject> holdingsRecord = findHoldingById(
      item.get().getString("holdingsRecordId"));

    if(!holdingsRecord.isPresent()) {
      return Optional.of(new InventoryRecords(item.get(), null, null, null, null));
    }
    
    final Optional<JsonObject> instance = findInstanceById(
      holdingsRecord.get().getString("instanceId"));

    return Optional.of(
      new InventoryRecords(item.get(), holdingsRecord.get(), instance.orElse(null),
        null, null));
  }

  public Optional<JsonObject> findHoldingById(String holdingsId) {
    return findById(holdingsId, holdings);
  }

  public Optional<JsonObject> findItemById(String itemId) {
    return findById(itemId, items);
  }

  public Optional<JsonObject> findInstanceById(String instanceId) {
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
