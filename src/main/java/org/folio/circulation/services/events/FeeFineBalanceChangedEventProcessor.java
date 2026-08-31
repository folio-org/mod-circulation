package org.folio.circulation.services.events;

import static org.folio.circulation.domain.subscribers.FeeFineBalanceChangedEvent.fromJson;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.subscribers.FeeFineBalanceChangedEvent;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FeeFineBalanceChangedEventProcessor implements KafkaEventProcessor {
  private static final String ACTUAL_COST_FEE_FINE_TYPE_ID = "73785370-d3bd-4d92-942d-ae2268e02ded";

  @Override
  public CompletableFuture<Result<Void>> process(JsonObject eventPayload,
    WebContext context, HttpClient client) {

    final var clients = create(context, client);
    final var scheduledNoticeService = FeeFineScheduledNoticeService.using(clients);

    return validateEventPayload(fromJson(eventPayload))
      .after(event -> {
        if (ACTUAL_COST_FEE_FINE_TYPE_ID.equals(event.getFeeFineTypeId())) {
          log.info("process:: scheduling notice for actual cost fee/fine, loanId={}",
            event.getLoanId());
          return scheduledNoticeService.scheduleNoticesForLostItemFeeActualCost(event);
        }

        log.info("process:: feeFineTypeId {} is not actual cost, skipping notice scheduling",
          event.getFeeFineTypeId());
        return emptyAsync();
      });
  }

  private Result<FeeFineBalanceChangedEvent> validateEventPayload(
    FeeFineBalanceChangedEvent eventPayload) {

    if (eventPayload.getLoanId() == null) {
      log.warn("validateEventPayload:: loanId is missing in event payload");
      return failed(singleValidationError(new ValidationError("Loan id is required",
        "loanId", null)));
    }
    if (eventPayload.getFeeFineId() == null) {
      log.warn("validateEventPayload:: feeFineId is missing in event payload");
      return failed(singleValidationError(new ValidationError("FeeFine id is required",
        "feeFineId", null)));
    }

    return succeeded(eventPayload);
  }
}
