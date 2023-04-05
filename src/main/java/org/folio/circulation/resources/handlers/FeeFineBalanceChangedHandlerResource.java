package org.folio.circulation.resources.handlers;

import static org.folio.circulation.domain.subscribers.FeeFineBalanceChangedEvent.fromJson;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.subscribers.FeeFineBalanceChangedEvent;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class FeeFineBalanceChangedHandlerResource extends Resource {

  private static final String ACTUAL_COST_FEE_FINE_TYPE_ID = "73785370-d3bd-4d92-942d-ae2268e02ded";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public FeeFineBalanceChangedHandlerResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/handlers/fee-fine-balance-changed", router)
      .create(this::handleFeeFineBalanceChangedEvent);
  }

  private void handleFeeFineBalanceChangedEvent(
    RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final var clients = create(context, client);
    final var scheduledNoticeService = FeeFineScheduledNoticeService.using(clients);

    validateEventPayload(fromJson(routingContext.body().asJsonObject()))
      .after(event -> {
        if (ACTUAL_COST_FEE_FINE_TYPE_ID.equals(event.getFeeFineTypeId())) {
          return scheduledNoticeService.scheduleNoticesForLostItemFeeActualCost(event);
        }
        return ofAsync(() -> null);
      })
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(result -> result.applySideEffect(context::write, failure -> {
        log.error("Cannot handle event [{}], error occurred {}",
          routingContext.getBodyAsString(), failure);
        context.write(noContent());
      }));
  }

  private Result<FeeFineBalanceChangedEvent> validateEventPayload(
    FeeFineBalanceChangedEvent eventPayload) {

    if (eventPayload.getLoanId() == null) {
      return failed(singleValidationError(new ValidationError("Loan id is required",
        "loanId", null)));
    }
    if (eventPayload.getFeeFineId() == null) {
      return failed(singleValidationError(new ValidationError("FeeFine id is required",
        "feeFineId", null)));
    }

    return succeeded(eventPayload);
  }
}
