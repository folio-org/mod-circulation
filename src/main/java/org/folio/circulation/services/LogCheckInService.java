package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.CheckInRecord;
import org.folio.circulation.infrastructure.storage.CheckInStorageRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;

public class LogCheckInService {
  private final CheckInStorageRepository checkInStorageRepository;

  public LogCheckInService(Clients clients) {
    this.checkInStorageRepository = new CheckInStorageRepository(clients);
  }

  public CompletableFuture<Result<CheckInContext>> logCheckInOperation(
    CheckInContext checkInContext) {

    final CheckInRecord checkInRecord = CheckInRecord.builder()
      .withOccurredDateTime(ClockManager.getClockManager().getDateTime())
      .withItemId(checkInContext.getItem().getItemId())
      .withServicePointId(checkInContext.getCheckInServicePointId().toString())
      .withPerformedByUserId(checkInContext.getLoggedInUserId())
      .withItemStatusPriorToCheckIn(checkInContext.getItem().getStatusName())
      .withItemLocationId(checkInContext.getItem().getLocationId())
      .withRequestQueueSize(checkInContext.getRequestQueue().size())
      .build();

    return checkInStorageRepository.createCheckInLogRecord(checkInRecord)
      .thenApply(result -> result.map(notUsed -> checkInContext));
  }
}
