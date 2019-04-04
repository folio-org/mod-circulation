package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.NoticeContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.Result.succeeded;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.storage.ItemByBarcodeInStorageFinder;
import org.folio.circulation.storage.SingleOpenLoanForItemInStorageFinder;
import org.folio.circulation.support.Result;

class CheckInProcessAdapter {
  private final ItemByBarcodeInStorageFinder itemFinder;
  private final SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder;
  private final LoanCheckInService loanCheckInService;
  private final RequestQueueRepository requestQueueRepository;
  private final UpdateItem updateItem;
  private final UpdateRequestQueue requestQueueUpdate;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final PatronNoticeService patronNoticeService;

  @SuppressWarnings("squid:S00107")
  CheckInProcessAdapter(
    ItemByBarcodeInStorageFinder itemFinder,
    SingleOpenLoanForItemInStorageFinder singleOpenLoanFinder,
    LoanCheckInService loanCheckInService,
    RequestQueueRepository requestQueueRepository,
    UpdateItem updateItem, UpdateRequestQueue requestQueueUpdate,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    PatronNoticeService patronNoticeService) {

    this.itemFinder = itemFinder;
    this.singleOpenLoanFinder = singleOpenLoanFinder;
    this.loanCheckInService = loanCheckInService;
    this.requestQueueRepository = requestQueueRepository;
    this.updateItem = updateItem;
    this.requestQueueUpdate = requestQueueUpdate;
    this.loanRepository = loanRepository;
    this.servicePointRepository = servicePointRepository;
    this.patronNoticeService = patronNoticeService;
  }

  CompletableFuture<Result<Item>> findItem(CheckInProcessRecords records) {
    return itemFinder.findItemByBarcode(records.getCheckInRequestBarcode());
  }

  CompletableFuture<Result<Loan>> findSingleOpenLoan(
    CheckInProcessRecords records) {

    return singleOpenLoanFinder.findSingleOpenLoan(records.getItem());
  }

  CompletableFuture<Result<Loan>> checkInLoan(CheckInProcessRecords records) {
    return completedFuture(
      loanCheckInService.checkIn(records.getLoan(), records.getCheckInRequest()));
  }

  CompletableFuture<Result<RequestQueue>> getRequestQueue(
    CheckInProcessRecords records) {

    return requestQueueRepository.get(records.getItem().getItemId());
  }

  CompletableFuture<Result<Item>> updateItem(CheckInProcessRecords records) {
    return updateItem.onCheckIn(records.getItem(), records.getRequestQueue(),
      records.getCheckInServicePointId());
  }

  CompletableFuture<Result<RequestQueue>> updateRequestQueue(
    CheckInProcessRecords records) {

    return requestQueueUpdate.onCheckIn(records.getRequestQueue(), records.getCheckInServicePointId().toString());
  }

  CompletableFuture<Result<Loan>> updateLoan(CheckInProcessRecords records) {
    // Loan must be updated after item
    // due to snapshot of item status stored with the loan
    // as this is how the loan action history is populated
    return loanRepository.updateLoan(records.getLoan());
  }

  CompletableFuture<Result<Item>> getDestinationServicePoint(CheckInProcessRecords records) {
    final Item item = records.getItem();

    if (item.getInTransitDestinationServicePointId() != null && item.getInTransitDestinationServicePoint() == null) {
      final UUID inTransitDestinationServicePointId = UUID.fromString(item.getInTransitDestinationServicePointId());
      return servicePointRepository.getServicePointById(inTransitDestinationServicePointId)
          .thenCompose(result ->
            result.after(servicePoint ->
              completedFuture(succeeded(updateItem.onDestinationServicePointUpdate(item, servicePoint))))
          );
    }

    return completedFuture(succeeded(item));
  }

  Result<CheckInProcessRecords> sendCheckInPatronNotice(CheckInProcessRecords records) {
    if (records.getLoan() == null) {
      return succeeded(records);
    }
    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(records.getItem())
      .withUser(records.getLoan().getUser())
      .withEventType(NoticeEventType.CHECK_IN)
      .withTiming(NoticeTiming.UPON_AT)
      .withNoticeContext(createLoanNoticeContext(records.getLoan()))
      .build();
    patronNoticeService.acceptNoticeEvent(noticeEvent);
    return succeeded(records);
  }

}
