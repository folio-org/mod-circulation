package org.folio.circulation.resources.subscribers;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent.fromJson;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.subscribers.LoanRelatedFeeFineClosedEvent;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class LoanRelatedFeeFineClosedSubscriberResource extends Resource {
  private static final List<String> LOST_ITEM_FEE_TYPES = Arrays.asList(
    LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE);

  public LoanRelatedFeeFineClosedSubscriberResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/subscribers/loan-related-fee-fina-closed", router)
      .create(this::handleFeeFineClosedEvent);
  }

  private void handleFeeFineClosedEvent(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    createAndValidateRequest(routingContext)
      .after(request -> processEvent(context, request))
      .thenApply(r -> r.toFixedValue(NoContentResponse::noContent))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<Loan>> processEvent(
    WebContext context, LoanRelatedFeeFineClosedEvent event) {

    final Clients clients = create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);

    return loanRepository.getById(event.getLoanId())
      .thenCompose(r -> r.after(loan -> {
        if (loan.isDeclaredLost()) {
          return closeDeclaredLostLoanIfLostFeesResolved(clients, loan);
        }

        return completedFuture(succeeded(loan));
      }));
  }

  private CompletableFuture<Result<Loan>> closeDeclaredLostLoanIfLostFeesResolved(
    Clients clients, Loan loan) {

    final AccountRepository accountRepository = new AccountRepository(clients);
    final LostItemPolicyRepository lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(clients);

    return accountRepository.findAccountsForLoan(loan)
      .thenComposeAsync(lostItemPolicyRepository::findLostItemPolicyForLoan)
      .thenCompose(r -> r.after(loanWithReferenceData -> {
        if (areLostFeesClosed(loanWithReferenceData)) {
          loanWithReferenceData.closeLoanAsLostAndPaid();

          return storeLoanAndItem.updateLoanAndItemInStorage(loanWithReferenceData);
        }

        return completedFuture(succeeded(loanWithReferenceData));
      }));
  }

  private boolean areLostFeesClosed(Loan loan) {
    if (loan.getLostItemPolicy().hasActualCostFee()) {
      // Actual cost fee is processed manually
      return false;
    }

    return loan.getAccounts().stream()
      .filter(account -> LOST_ITEM_FEE_TYPES.contains(account.getFeeFineType()))
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
