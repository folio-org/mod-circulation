package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.services.LostItemFeeRefundContext.using;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeRefundService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeRefundService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineFacade feeFineFacade;
  private final AccountRepository accountRepository;
  private final LoanRepository loanRepository;

  public LostItemFeeRefundService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.accountRepository = new AccountRepository(clients);
    this.loanRepository = new LoanRepository(clients);
  }

  public CompletableFuture<Result<CheckInContext>> refundLostItemFees(
    CheckInContext context) {

    final LostItemFeeRefundContext refundFeeContext = using(context);

    return refundLostItemFees(refundFeeContext)
      .thenApply(r -> r.map(context::withLostItemFeesRefundedOrCancelled));
  }

  public CompletableFuture<Result<RenewalContext>> refundLostItemFees(
    RenewalContext renewalContext, String currentServicePointId) {

    final LostItemFeeRefundContext refundFeeContext = using(renewalContext,
      currentServicePointId);

    return refundLostItemFees(refundFeeContext)
      .thenApply(r -> r.map(renewalContext::withLostItemFeesRefundedOrCancelled));
  }

  private CompletableFuture<Result<Boolean>> refundLostItemFees(LostItemFeeRefundContext refundFeeContext) {
    if (!refundFeeContext.shouldRefundFeesForItem()) {
      return completedFuture(succeeded(false));
    }

    return lookupLoan(succeeded(refundFeeContext))
      .thenCompose(this::fetchLostItemPolicy)
      .thenCompose(contextResult -> contextResult.after(context -> {
        final LostItemPolicy lostItemPolicy = context.getLostItemPolicy();

        if (!lostItemPolicy.shouldRefundFees(context.getItemLostDate())) {
          log.info("Refund interval has exceeded for loan [{}]", context.getLoan().getId());
          return completedFuture(succeeded(false));
        }

        return fetchAccountsAndActionsForLoan(contextResult)
          .thenCompose(r -> r.after(this::refundAccounts));
      }));
  }

  private CompletableFuture<Result<Boolean>> refundAccounts(LostItemFeeRefundContext context) {
    return feeFineFacade.refundAndCloseAccounts(context.accountRefundCommands())
      .thenApply(r -> r.map(notUsed -> context.anyAccountNeedsRefund()));
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> lookupLoan(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.after(context -> {
      if (context.hasLoan()) {
        return completedFuture(succeeded(context));
      }

      return loanRepository.findLastLoanForItem(context.getItemId())
        .thenApply(r -> r.next(loan -> {
          if (loan == null) {
            log.error("There are no loans for lost item [{}]", context.getItemId());
            return noLoanFoundForLostItem(context.getItemId());
          }

          if (loan.getDeclareLostDateTime() == null) {
            log.error("The last loan [{}] for lost item [{}] is not declared lost",
              loan.getId(), context.getItemId());
            return lastLoanForLostItemIsNotDeclaredLost(loan);
          }

          log.info("Loan [{}] retrieved for lost item [{}]", loan.getId(), context.getItemId());
          return succeeded(context.withLoan(loan));
        }));
    });
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> fetchAccountsAndActionsForLoan(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.after(context -> {
      final Result<CqlQuery> fetchQuery = exactMatch("loanId", context.getLoan().getId())
        .combine(exactMatchAny("feeFineType", lostItemFeeTypes()), CqlQuery::and);

      return accountRepository.findAccountsAndActionsForLoanByQuery(fetchQuery)
        .thenApply(r -> r.map(context::withAccounts));
    });
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> fetchLostItemPolicy(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.combineAfter(
      context -> lostItemPolicyRepository
        .getLostItemPolicyById(context.getLoan().getLostItemPolicyId()),
      LostItemFeeRefundContext::withLostItemPolicy);
  }

  private Result<LostItemFeeRefundContext> lastLoanForLostItemIsNotDeclaredLost(Loan loan) {
    return failed(singleValidationError(
      "Last loan for lost item is not declared lost", "loanId", loan.getId()));
  }

  private Result<LostItemFeeRefundContext> noLoanFoundForLostItem(String itemId) {
    return failed(singleValidationError(
      "Item is lost however there is no declared lost loan found",
      "itemId", itemId));
  }
}
