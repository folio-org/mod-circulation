package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Optional;

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

  public Optional<JsonObject> findHoldingById(String holdingsId) {
    return findById(holdingsId, holdings);
  }

  public Optional<JsonObject> findItemById(String itemId) {
    return findById(itemId, items);
  }

  public Optional<JsonObject> findInstanceById(String instanceId) {
    return findById(instanceId, instances);
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

  private Optional<JsonObject> findById(
    String id,
    Collection<JsonObject> collection) {

    return collection.stream()
      .filter(item -> item.getString("id").equals(id))
      .findFirst();
  }
}
