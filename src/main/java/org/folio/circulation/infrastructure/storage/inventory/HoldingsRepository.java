package org.folio.circulation.infrastructure.storage.inventory;

import org.folio.circulation.support.CollectionResourceClient;

public class HoldingsRepository {
  private final CollectionResourceClient holdingsClient;

  public HoldingsRepository(CollectionResourceClient holdingsClient) {
    this.holdingsClient = holdingsClient;
  }
}
