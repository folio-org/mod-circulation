package org.folio.circulation.services.events;

import static org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent.fromJson;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class LoanRelatedFeeFineClosedEventProcessor {

  public CompletableFuture<Result<Void>> process(JsonObject eventPayload,
    WebContext context, HttpClient client) {

    final var clients = create(context, client);
    final var eventPublisher = new EventPublisher(context, clients);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var closeLoanWithLostItemService = new CloseLoanWithLostItemService(loanRepository,
      itemRepository, new AccountRepository(clients), new LostItemPolicyRepository(clients),
      eventPublisher, new ActualCostRecordRepository(clients));

    return createAndValidateRequest(eventPayload)
      .after(event -> processEvent(loanRepository, event, closeLoanWithLostItemService));
  }

  private CompletableFuture<Result<Void>> processEvent(LoanRepository loanRepository,
    LoanRelatedFeeFineClosedEvent event, CloseLoanWithLostItemService closeLoanWithLostItemService) {

    log.info("processEvent:: loanId={}", event.getLoanId());

    return loanRepository.getById(event.getLoanId())
      .thenCompose(r -> r.after(closeLoanWithLostItemService::closeLoanAsLostAndPaid));
  }

  private Result<LoanRelatedFeeFineClosedEvent> createAndValidateRequest(JsonObject eventPayload) {
    final LoanRelatedFeeFineClosedEvent event = fromJson(eventPayload);

    if (event.getLoanId() == null) {
      log.warn("createAndValidateRequest:: loanId is missing in event payload");
      return failed(singleValidationError(
        new ValidationError("Loan id is required", "loanId", null)));
    }

    return succeeded(event);
  }
}
