package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.HttpResult;

class CheckInProcessAdapter {
  private final ItemByBarcodeInStorageFinder itemFinder;
  private final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder;
  private final LoanCheckInService loanCheckInService;
  private final RequestQueueRepository requestQueueRepository;
  private final UpdateItem updateItem;

  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder,
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder,
    LoanCheckInService loanCheckInService,
    RequestQueueRepository requestQueueRepository,
    UpdateItem updateItem) {

    this.itemFinder = itemFinder;
    this.singleOpenLoanFinder = singleOpenLoanFinder;
    this.loanCheckInService = loanCheckInService;
    this.requestQueueRepository = requestQueueRepository;
    this.updateItem = updateItem;
  }

  CompletableFuture<HttpResult<Item>> findItem(CheckInProcessRecords records) {
    return itemFinder.findItemByBarcode(records.getCheckInRequestBarcode());
  }

  CompletableFuture<HttpResult<Loan>> findSingleOpenLoan(
    CheckInProcessRecords records) {

    return singleOpenLoanFinder.findSingleOpenLoan(records.getItem());
  }

  CompletableFuture<HttpResult<Loan>> checkInLoan(CheckInProcessRecords records) {
    return completedFuture(
      loanCheckInService.checkIn(records.getLoan(), records.getCheckInRequest()));
  }

  CompletableFuture<HttpResult<RequestQueue>> getRequestQueue(
    CheckInProcessRecords records) {

    return requestQueueRepository.get(records.getItem().getItemId());
  }

  CompletableFuture<HttpResult<Item>> updateItem(CheckInProcessRecords records) {
    return updateItem.onCheckIn(records.getItem(), records.getRequestQueue());
  }
}
