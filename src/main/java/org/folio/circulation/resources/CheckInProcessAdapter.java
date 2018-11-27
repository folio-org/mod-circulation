package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.support.HttpResult;

class CheckInProcessAdapter {
  private final ItemByBarcodeInStorageFinder itemFinder;

  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder) {

    this.itemFinder = itemFinder;
  }

  CompletableFuture<HttpResult<Item>> findItem(CheckInProcessRecords records) {
    return itemFinder.findItemByBarcode(records.getCheckInRequestBarcode());
  }
}
