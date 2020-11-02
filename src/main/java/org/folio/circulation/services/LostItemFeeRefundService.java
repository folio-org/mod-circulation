package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.services.LostItemFeeRefundContext.forCheckIn;
import static org.folio.circulation.services.LostItemFeeRefundContext.forRenewal;
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
    CheckInContext checkInContext) {

    return refundLostItemFees(forCheckIn(checkInContext))
      .thenApply(r -> r.map(context -> {
        // check-in of item without an open loan (the only possible case is Lost and paid items)
        // for such check-in we should not set loan in response.
        if (checkInContext.getLoan() == null) {
          return checkInContext;
        }

        return checkInContext.withLoan(context.getLoan());
      }));
  }

  public CompletableFuture<Result<RenewalContext>> refundLostItemFees(
    RenewalContext renewalContext, String currentServicePointId) {

    return refundLostItemFees(forRenewal(renewalContext, currentServicePointId))
      .thenApply(r -> r.map(context -> renewalContext.withLoan(context.getLoan())));
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> refundLostItemFees(
    LostItemFeeRefundContext refundFeeContext) {

    if (!refundFeeContext.shouldRefundFeesForItem()) {
      return completedFuture(succeeded(refundFeeContext));
    }

    return lookupLoan(succeeded(refundFeeContext))
      .thenCompose(this::fetchLostItemPolicy)
      .thenCompose(contextResult -> contextResult.after(context -> {
        final LostItemPolicy lostItemPolicy = context.getLostItemPolicy();

        if (!lostItemPolicy.shouldRefundFees(context.getItemLostDate())) {
          log.info("Refund interval has exceeded for loan [{}]", context.getLoan().getId());
          return completedFuture(succeeded(context));
        }

        return fetchAccountsAndActionsForLoan(contextResult)
          .thenCompose(r -> r.after(this::refundAccounts));
      }));
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> refundAccounts(LostItemFeeRefundContext context) {
    return feeFineFacade.refundAndCloseAccounts(context.accountRefundCommands())
      .thenApply(r -> r.map(notUsed -> context));
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

          if (loan.getLostDate() == null) {
            log.error("The last loan [{}] for lost item [{}] is neither aged to lost not declared lost",
              loan.getId(), context.getItemId());
            return lastLoanForLostItemIsNotLost(loan);
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

  private Result<LostItemFeeRefundContext> lastLoanForLostItemIsNotLost(Loan loan) {
    return failed(singleValidationError(
      "Last loan for lost item is neither aged to lost nor declared lost",
      "loanId", loan.getId()));
  }

  private Result<LostItemFeeRefundContext> noLoanFoundForLostItem(String itemId) {
    return failed(singleValidationError(
      "Item is lost however there is no aged to lost nor declared lost loan found",
      "itemId", itemId));
  }
}
