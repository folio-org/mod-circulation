package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
<<<<<<< HEAD
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
=======
import org.folio.circulation.domain.Loan;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
>>>>>>> cf88b8b1... Use process adapter to simplify finding an open loan during check in CIRC-146
import org.folio.circulation.support.HttpResult;

class CheckInProcessAdapter {
  private final ItemByBarcodeInStorageFinder itemFinder;
<<<<<<< HEAD

  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder) {

    this.itemFinder = itemFinder;
=======
  private final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder;

  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder,
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder) {

    this.itemFinder = itemFinder;
    this.singleOpenLoanFinder = singleOpenLoanFinder;
>>>>>>> cf88b8b1... Use process adapter to simplify finding an open loan during check in CIRC-146
  }

  CompletableFuture<HttpResult<Item>> findItem(CheckInProcessRecords records) {
    return itemFinder.findItemByBarcode(records.getCheckInRequestBarcode());
  }
<<<<<<< HEAD
=======

  CompletableFuture<HttpResult<Loan>> findSingleOpenLoan(
    CheckInProcessRecords records) {

    return singleOpenLoanFinder.findSingleOpenLoan(records.getItem());
  }
>>>>>>> cf88b8b1... Use process adapter to simplify finding an open loan during check in CIRC-146
}
