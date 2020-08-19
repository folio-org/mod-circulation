package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.LOST_AND_PAID;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.support.RefundAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.joda.time.DateTime;
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

    final LostItemFeeRefundContext refundFeeContext = new LostItemFeeRefundContext(
      context.getItemStatusBeforeCheckIn(), context.getItem().getItemId(),
      context.getLoan(), context.getLoggedInUserId(),
      context.getCheckInServicePointId().toString());

    return refundLostItemFees(refundFeeContext)
      .thenApply(r -> r.map(context::withLostItemFeesRefundedOrCancelled));
  }

  public CompletableFuture<Result<RenewalContext>> refundLostItemFees(
    RenewalContext renewalContext, String currentServicePointId) {

    final LostItemFeeRefundContext refundFeeContext = new LostItemFeeRefundContext(
      renewalContext.getItemStatusBeforeRenewal(), renewalContext.getLoan().getItemId(),
      renewalContext.getLoan(), renewalContext.getLoggedInUserId(), currentServicePointId);

    return refundLostItemFees(refundFeeContext)
      .thenApply(r -> r.map(renewalContext::withLostItemFeesRefundedOrCancelled));
  }

  private CompletableFuture<Result<Boolean>> refundLostItemFees(LostItemFeeRefundContext refundFeeContext) {
    if (!isItemLost(refundFeeContext)) {
      return completedFuture(succeeded(false));
    }

    return lookupLoan(succeeded(refundFeeContext))
      .thenCompose(this::fetchLostItemPolicy)
      .thenCompose(contextResult -> contextResult.after(context -> {
        final DateTime declaredLostDate = context.loan.getDeclareLostDateTime();
        final LostItemPolicy lostItemPolicy = context.lostItemPolicy;

        if (!lostItemPolicy.shouldRefundFees(declaredLostDate)) {
          log.debug("Refund interval has exceeded for loan [{}]", context.loan.getId());
          return completedFuture(succeeded(false));
        }

        return fetchAccountsAndActionsForLoan(contextResult)
          .thenCompose(r -> r.after(notUsed -> feeFineFacade
            .refundAndCloseAccounts(context.accountRefundCommands())))
          .thenApply(r -> r.map(notUsed -> context.anyAccountNeedsRefund()));
      }));
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> lookupLoan(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.after(context -> {
      if (isOpenLoanAlreadyFound(context)) {
        return completedFuture(succeeded(context));
      }

      return loanRepository.findLastLoanForItem(context.itemId)
        .thenApply(r -> r.next(loan -> {
          if (loan == null) {
            log.error("There are no loans for lost item [{}]", context.itemId);
            return noLoanFoundForLostItem(context.itemId);
          }

          if (loan.getDeclareLostDateTime() == null) {
            log.error("The last loan [{}] for lost item [{}] is not declared lost",
              loan.getId(), context.itemId);
            return lastLoanForLostItemIsNotDeclaredLost(loan);
          }

          log.info("Loan [{}] retrieved for lost item [{}]", loan.getId(), context.itemId);
          return succeeded(context.withLoan(loan));
        }));
    });
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> fetchAccountsAndActionsForLoan(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.after(context -> {
      final Result<CqlQuery> fetchQuery = exactMatch("loanId", context.loan.getId())
        .combine(exactMatchAny("feeFineType", lostItemFeeTypes()), CqlQuery::and);

      return accountRepository.findAccountsAndActionsForLoanByQuery(fetchQuery)
        .thenApply(r -> r.map(context::withAccounts));
    });
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> fetchLostItemPolicy(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.combineAfter(
      context -> lostItemPolicyRepository.getLostItemPolicyById(context.loan.getLostItemPolicyId()),
      LostItemFeeRefundContext::withLostItemPolicy);
  }

  private boolean isItemLost(LostItemFeeRefundContext context) {
    return context.itemStatus == DECLARED_LOST || context.itemStatus == LOST_AND_PAID;
  }

  private boolean isOpenLoanAlreadyFound(LostItemFeeRefundContext context) {
    return context.loan != null;
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

  private static final class LostItemFeeRefundContext {
    private final ItemStatus itemStatus;
    private final String itemId;
    private final String staffUserId;
    private final String servicePointId;
    private final Loan loan;
    private Collection<Account> accounts;
    private LostItemPolicy lostItemPolicy;

    private LostItemFeeRefundContext(ItemStatus itemStatus, String itemId, Loan loan,
                                     String staffUserId, String servicePointId) {

      this.itemStatus = itemStatus;
      this.itemId = itemId;
      this.loan = loan;
      this.staffUserId = staffUserId;
      this.servicePointId = servicePointId;
    }

    private LostItemFeeRefundContext withAccounts(Collection<Account> accounts) {
      this.accounts = accounts;
      return this;
    }

    private LostItemFeeRefundContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
      this.lostItemPolicy = lostItemPolicy;
      return this;
    }

    private LostItemFeeRefundContext withLoan(Loan loan) {
      return new LostItemFeeRefundContext(itemStatus, itemId, loan, staffUserId, servicePointId)
        .withAccounts(accounts)
        .withLostItemPolicy(lostItemPolicy);
    }

    private Collection<Account> accountsNeedingRefunds() {
      if (!lostItemPolicy.isRefundProcessingFeeWhenReturned()) {
        return accounts.stream()
          .filter(account -> !account.getFeeFineType().equals(LOST_ITEM_PROCESSING_FEE_TYPE))
          .collect(Collectors.toList());
      }

      return accounts;
    }

    private List<RefundAccountCommand> accountRefundCommands() {
      return accountsNeedingRefunds().stream()
        .map(account -> new RefundAccountCommand(account, staffUserId, servicePointId))
        .collect(Collectors.toList());
    }

    private boolean anyAccountNeedsRefund() {
      return accountsNeedingRefunds().size() > 0;
    }
  }
}
