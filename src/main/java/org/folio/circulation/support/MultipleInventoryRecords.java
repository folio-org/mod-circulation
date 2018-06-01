package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class MultipleInventoryRecords {
  private final Collection<JsonObject> items;
  private final Collection<JsonObject> holdings;
  private final Collection<JsonObject> instances;
  private final Collection<Item> records;

  MultipleInventoryRecords(
    Collection<JsonObject> items,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances,
    Collection<Item> records) {

    this.items = items;
    this.holdings = holdings;
    this.instances = instances;
    this.records = records;
  }

  public static MultipleInventoryRecords from(
    Collection<JsonObject> items,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances) {

    return new MultipleInventoryRecords(items, holdings, instances,
      items.stream()
        .map(item -> itemToInventoryRecords(item, holdings, instances))
        .collect(Collectors.toList()));
  }

  private static Item itemToInventoryRecords(
    JsonObject item,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances) {

    final Optional<JsonObject> holdingsRecord = findById(
      item.getString("holdingsRecordId"), holdings);

    if(!holdingsRecord.isPresent()) {
      return new Item(item, null, null, null, null);
    }

    final Optional<JsonObject> instance = findById(
      holdingsRecord.get().getString("instanceId"), instances);

    return
      new Item(item, holdingsRecord.get(), instance.orElse(null),
        null, null);
  }

  public Collection<Item> getRecords() {
    return records;
  }

  public Item findRecordByItemId(String itemId) {
    return records.stream()
      .filter(record -> record.getItemId().equals(itemId))
      .findFirst()
      .orElseGet(() -> new Item(null, null, null, null, null));
  }

  private static Optional<JsonObject> findById(
    String id,
    Collection<JsonObject> collection) {

    return collection.stream()
      .filter(item -> item.getString("id").equals(id))
      .findFirst();
  }

  public Collection<JsonObject> getItems() {
    return items;
  }

  public Collection<JsonObject> getHoldings() {
    return holdings;
  }

  public Collection<JsonObject> getInstances() {
    return instances;
  }
}
