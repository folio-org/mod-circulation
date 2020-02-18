package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.CheckInRecord;
import org.folio.circulation.storage.CheckInStorageRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;

public class LogCheckInService {
  private final CheckInStorageRepository checkInStorageRepository;

  public LogCheckInService(Clients clients) {
    this.checkInStorageRepository = new CheckInStorageRepository(clients);
  }

  public CompletableFuture<Result<CheckInProcessRecords>> logCheckInOperation(
    CheckInProcessRecords checkInProcessRecords) {

    final CheckInRecord checkInRecord = CheckInRecord.builder()
      .withOccurredDateTime(ClockManager.getClockManager().getDateTime())
      .withItemId(checkInProcessRecords.getItem().getItemId())
      .withServicePointId(checkInProcessRecords.getCheckInServicePointId().toString())
      .withPerformedByUserId(checkInProcessRecords.getLoggedInUserId())
      .build();

    return checkInStorageRepository.createCheckInLogRecord(checkInRecord)
      .thenApply(result -> result.map(notUsed -> checkInProcessRecords));
  }
}
