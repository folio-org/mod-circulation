package org.folio.circulation.resources.handlers;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent.fromJson;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Account;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoanRelatedFeeFineClosedHandlerResource extends Resource {
  private static final Logger log = LoggerFactory.getLogger(
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
    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    createAndValidateRequest(routingContext)
      .after(request -> processEvent(context, request))
      .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(result -> result.applySideEffect(context::write, failure -> {
        log.error("Cannot handle event [{}], error occurred {}",
          routingContext.getBodyAsString(), failure);

        context.write(noContent());
      }));
  }

  private CompletableFuture<Result<Loan>> processEvent(
    WebContext context, LoanRelatedFeeFineClosedEvent event) {

    final Clients clients = create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);

    return loanRepository.getById(event.getLoanId())
      .thenCompose(r -> r.after(loan -> {
        if (loan.isItemLost()) {
          return closeLoanWithLostItemIfLostFeesResolved(clients, loan);
        }

        return completedFuture(succeeded(loan));
      }));
  }

  private CompletableFuture<Result<Loan>> closeLoanWithLostItemIfLostFeesResolved(
    Clients clients, Loan loan) {

    final AccountRepository accountRepository = new AccountRepository(clients);
    final LostItemPolicyRepository lostItemPolicyRepository = new LostItemPolicyRepository(clients);

    return accountRepository.findAccountsForLoan(loan)
      .thenComposeAsync(lostItemPolicyRepository::findLostItemPolicyForLoan)
      .thenCompose(loanResult -> closeLoanAndUpdateItem(loanResult, clients));
  }

  public CompletableFuture<Result<Loan>> closeLoanAndUpdateItem(
    Result<Loan> loanResult, Clients clients) {

    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(clients);
    return loanResult.after(loan -> {
      if (allLostFeesClosed(loan)) {
        loan.closeLoanAsLostAndPaid();
        return storeLoanAndItem.updateLoanAndItemInStorage(loan);
      }

      return completedFuture(succeeded(loan));
    });
  }

  private boolean allLostFeesClosed(Loan loan) {
    if (loan.getLostItemPolicy().hasActualCostFee()) {
      // Actual cost fee is processed manually
      return false;
    }

    return loan.getAccounts().stream()
      .filter(account -> lostItemFeeTypes().contains(account.getFeeFineType()))
      .allMatch(Account::isClosed);
  }

  private Result<LoanRelatedFeeFineClosedEvent> createAndValidateRequest(RoutingContext context) {
    final LoanRelatedFeeFineClosedEvent eventPayload = fromJson(context.getBodyAsJson());

    if (eventPayload.getLoanId() == null) {
      return failed(singleValidationError(
        new ValidationError("Loan id is required", "loanId", null)));
    }

    return succeeded(eventPayload);
  }
}
