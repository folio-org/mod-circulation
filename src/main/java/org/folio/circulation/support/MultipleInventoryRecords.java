package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Optional;

public class MultipleInventoryRecords {
  private final Collection<JsonObject> items;
  private final Collection<JsonObject> holdings;
  private final Collection<JsonObject> instances;

  public MultipleInventoryRecords(
    Collection<JsonObject> items,
    Collection<JsonObject> holdings,
    Collection<JsonObject> instances) {

    this.items = items;
    this.holdings = holdings;
    this.instances = instances;
  }

  public Optional<JsonObject> findHoldingById(String holdingsId) {
    return getHoldings().stream()
      .filter(holding -> holding.getString("id").equals(holdingsId))
      .findFirst();
  }

  public Optional<JsonObject> findItemById(String itemId) {
    return getItems().stream()
      .filter(item -> item.getString("id").equals(itemId))
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
