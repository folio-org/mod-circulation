package org.folio.circulation.services;

import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInOperation;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.storage.CheckInOperationRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckInOperationService {
  private static final Logger log = LoggerFactory.getLogger(CheckInOperationService.class);

  private final CheckInOperationRepository checkInOperationRepository;

  public CheckInOperationService(Clients clients) {
    this.checkInOperationRepository = new CheckInOperationRepository(clients);
  }

  public CompletableFuture<Result<CheckInProcessRecords>> logCheckInOperation(
    CheckInProcessRecords checkInProcessRecords) {

    CompletableFuture.runAsync(() -> {
      final CheckInOperation checkInOperation = CheckInOperation.builder()
        .withOccurredDateTime(ClockManager.getClockManager().getDateTime())
        .withItemId(checkInProcessRecords.getItem().getItemId())
        .withCheckInServicePointId(checkInProcessRecords.getCheckInServicePointId().toString())
        .withPerformedByUserId(checkInProcessRecords.getLoggedInUserId())
        .build();

      logCheckInOperation(checkInOperation);
    });

    return CompletableFuture.completedFuture(succeeded(checkInProcessRecords));
  }

  private void logCheckInOperation(CheckInOperation checkInOperation) {
    checkInOperationRepository.logCheckInOperation(checkInOperation)
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
