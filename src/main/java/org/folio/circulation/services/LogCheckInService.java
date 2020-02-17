package org.folio.circulation.services;

import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInLogRecord;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.storage.CheckInStorageRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogCheckInService {
  private static final Logger log = LoggerFactory.getLogger(LogCheckInService.class);

  private final CheckInStorageRepository checkInStorageRepository;

  public LogCheckInService(Clients clients) {
    this.checkInStorageRepository = new CheckInStorageRepository(clients);
  }

  public CompletableFuture<Result<CheckInProcessRecords>> logCheckInOperation(
    CheckInProcessRecords checkInProcessRecords) {

    CompletableFuture.runAsync(() -> {
      final CheckInLogRecord checkInLogRecord = CheckInLogRecord.builder()
        .withOccurredDateTime(ClockManager.getClockManager().getDateTime())
        .withItemId(checkInProcessRecords.getItem().getItemId())
        .withCheckInServicePointId(checkInProcessRecords.getCheckInServicePointId().toString())
        .withPerformedByUserId(checkInProcessRecords.getLoggedInUserId())
        .build();

      logCheckInOperation(checkInLogRecord);
    });

    return CompletableFuture.completedFuture(succeeded(checkInProcessRecords));
  }

  private void logCheckInOperation(CheckInLogRecord checkInLogRecord) {
    checkInStorageRepository.createCheckInLogRecord(checkInLogRecord)
      .handle((result, ex) -> {
        if (result.failed() || ex != null) {
          logCheckInFailure(result.cause(), ex);
        } else {
          log.debug("Check-in operation recorded successfully");
        }

        return null;
      });
  }

  private void logCheckInFailure(HttpFailure httpFailure, Throwable ex) {
    if (ex != null) {
      log.warn("Unable to record check-in operation, exception occurred", ex);
    }

    if (httpFailure != null) {
      log.warn("Unable to record check-in operation, failure [{}]", httpFailure.getReason());
    }
  }
}
