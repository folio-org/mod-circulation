package org.folio.circulation.resources.handlers;

import static org.folio.circulation.domain.EventType.LOAN_RELATED_FEE_FINE_CLOSED;
import static org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent.fromJson;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizationDueDateStorageRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoanRelatedFeeFineClosedHandlerResource extends Resource {
  private static final Logger log = LogManager.getLogger(
    LoanRelatedFeeFineClosedHandlerResource.class);

  public LoanRelatedFeeFineClosedHandlerResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/handlers/loan-related-fee-fine-closed", router)
      .create(this::handleFeeFineClosedEvent);
  }

  private void handleFeeFineClosedEvent(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final var clients = create(context, client);
    final var eventPublisher = new EventPublisher(context, clients);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var closeLoanWithLostItemService = new CloseLoanWithLostItemService(loanRepository,
      itemRepository, new AccountRepository(clients), new LostItemPolicyRepository(clients),
      eventPublisher, new ActualCostRecordRepository(clients));

    log.info("Event {} received: {}", LOAN_RELATED_FEE_FINE_CLOSED, routingContext.body().asString());

    final var dueDateStorageRepository = new AnonymizationDueDateStorageRepository(clients);

    createAndValidateRequest(routingContext)
      .after(request -> clearAnonymizationDueDate(dueDateStorageRepository, request)
        .thenCompose(r -> r.after(notUsed ->
          processEvent(loanRepository, request, closeLoanWithLostItemService))))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(result -> result.applySideEffect(context::write, failure -> {
        log.error("Cannot handle event [{}], error occurred {}",
          routingContext.body().asString(), failure);

        context.write(noContent());
      }));
  }

  /**
   * A fee/fine closing changes the loan's anonymization eligibility, so clear
   * its due-date row and let the next sweep re-evaluate it. Never fails the
   * event: a missed clear only delays re-evaluation.
   */
  private CompletableFuture<Result<Integer>> clearAnonymizationDueDate(
    AnonymizationDueDateStorageRepository dueDateStorageRepository,
    LoanRelatedFeeFineClosedEvent event) {

    return dueDateStorageRepository.clearByLoanIds(List.of(event.getLoanId()))
      .thenApply(r -> r.mapFailure(failure -> {
        log.warn("clearAnonymizationDueDate:: clearing stamp failed for loan {}: {}",
          event.getLoanId(), failure);
        return succeeded(0);
      }));
  }

  private CompletableFuture<Result<Void>> processEvent(LoanRepository loanRepository,
    LoanRelatedFeeFineClosedEvent event, CloseLoanWithLostItemService closeLoanWithLostItemService) {

    log.info("processEvent:: loanId={}", event.getLoanId());

    return loanRepository.getById(event.getLoanId())
      .thenCompose(r -> r.after(closeLoanWithLostItemService::closeLoanAsLostAndPaid));
  }

  private Result<LoanRelatedFeeFineClosedEvent> createAndValidateRequest(RoutingContext context) {
    final LoanRelatedFeeFineClosedEvent eventPayload = fromJson(context.body().asJsonObject());

    if (eventPayload.getLoanId() == null) {
      log.warn("createAndValidateRequest:: loanId is missing in event payload");
      return failed(singleValidationError(
        new ValidationError("Loan id is required", "loanId", null)));
    }

    return succeeded(eventPayload);
  }
}
