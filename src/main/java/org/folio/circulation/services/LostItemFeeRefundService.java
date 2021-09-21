package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeRefundContext.forCheckIn;
import static org.folio.circulation.services.LostItemFeeRefundContext.forRenewal;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.joda.time.Seconds.secondsBetween;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.feefine.AccountActionResponse;
import org.folio.circulation.services.support.RefundAndCancelAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;

public class LostItemFeeRefundService {
  private static final Logger log = LogManager.getLogger(LostItemFeeRefundService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineFacade feeFineFacade;
  private final AccountRepository accountRepository;
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final ItemRepository itemRepository;
  private final FeeFineScheduledNoticeService scheduledNoticeService;

  public LostItemFeeRefundService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.accountRepository = new AccountRepository(clients);
    this.loanRepository = new LoanRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.itemRepository = new ItemRepository(clients, true, false, false);
    this.scheduledNoticeService = FeeFineScheduledNoticeService.using(clients);
  }

  public CompletableFuture<Result<CheckInContext>> refundLostItemFees(
    CheckInContext checkInContext) {

    return refundLostItemFees(forCheckIn(checkInContext))
      .thenApply(r -> r.map(context -> {
        // Refunds may operate on open or closed loans,
        // closed loans are not affected during check in
        // so should not be included in the API response
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

  public CompletableFuture<Result<LostItemFeeRefundContext>> refundLostItemFees(
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

        return fetchAccountsAndActionsForLoan(context)
          .thenCompose(r -> r.after(this::refundAccounts))
          .exceptionally(CommonFailures::failedDueToServerError);
      }));
  }

  public CompletableFuture<Result<LostItemFeeRefundContext>> refundAccounts(
    LostItemFeeRefundContext context) {

    return succeeded(context.accountRefundCommands())
      .after(commands -> allOf(commands, command -> refundAndCloseAccount(context, command)))
      .thenApply(r -> r.map(notUsed -> context));
  }

  private CompletableFuture<Result<Void>> refundAndCloseAccount(LostItemFeeRefundContext context,
    RefundAndCancelAccountCommand command) {

    return userRepository.getUser(command.getStaffUserId())
      .thenCompose(r -> r.after(user -> processAccount(context, command, user)));
  }

  private CompletableFuture<Result<Void>> processAccount(LostItemFeeRefundContext context,
    RefundAndCancelAccountCommand command, User user) {

    return feeFineFacade.refundAccountIfNeeded(command, user)
      .thenApply(r -> r.next(response -> schedulePatronNotices(context, response)))
      .thenCompose(r -> r.after(notUsed -> feeFineFacade.cancelAccountIfNeeded(command, user)))
      .thenApply(r -> r.next(response -> schedulePatronNotices(context, response)));
  }

  private Result<Void> schedulePatronNotices(LostItemFeeRefundContext context,
    AccountActionResponse response) {

    if (shouldSchedulePatronNotices(context, response)) {
      response.getFeeFineActions().stream()
        // no patron notices should be created for "credit" actions
        .filter(not(FeeFineAction::isCredited))
        .forEach(ffa -> scheduledNoticeService.scheduleAgedToLostReturnedNotices(context, ffa));
    }

    return succeeded(null);
  }

  private static boolean shouldSchedulePatronNotices(LostItemFeeRefundContext context,
    AccountActionResponse response) {

    return context.getCancelReason() == CANCELLED_ITEM_RETURNED
      && response != null
      && isNotEmpty(response.getFeeFineActions());
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> lookupLoan(
    Result<LostItemFeeRefundContext> contextResult) {

    return contextResult.after(context -> {
      if (context.hasLoan()) {
        return completedFuture(succeeded(context));
      }

      return loanRepository.findLastLoanForItem(context.getItemId())
        // we will need user and item to find Patron Notice Policy while scheduling notices later
        .thenCompose(r -> r.after(this::fetchUserAndItem))
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

  private CompletableFuture<Result<Loan>> fetchUserAndItem(Loan loan) {
    if (loan == null) {
      return ofAsync(() -> null);
    }

    return userRepository.findUserForLoan(succeeded(loan))
      .thenCompose(r -> r.combineAfter(itemRepository::fetchFor, Loan::withItem));
  }

  private CompletableFuture<Result<LostItemFeeRefundContext>> fetchAccountsAndActionsForLoan(
    LostItemFeeRefundContext context) {

    List<String> feeFineTypes = new ArrayList<>();
    feeFineTypes.add(LOST_ITEM_FEE_TYPE);
    if (context.getLostItemPolicy().isRefundProcessingFeeWhenReturned()) {
      feeFineTypes.add(LOST_ITEM_PROCESSING_FEE_TYPE);
    }

    final Result<CqlQuery> fetchLostItemFeeQuery = exactMatch("loanId", context.getLoan().getId())
      .combine(exactMatchAny("feeFineType", feeFineTypes), CqlQuery::and);

      return accountRepository.findAccountsAndActionsForLoanByQuery(fetchLostItemFeeQuery)
        .thenApply(r -> r.map(this::filterAccountsForRefund))
        .thenApply(r -> r.map(context::withAccounts));
  }

  private Collection<Account> filterAccountsForRefund(Collection<Account> accounts) {

    Account latestLostItemFeeAccount = getMostRecentAccount(accounts, LOST_ITEM_FEE_TYPE);

    if (latestLostItemFeeAccount != null
      && latestLostItemFeeAccount.getPaymentStatus() != null
      && latestLostItemFeeAccount.getCreationDate() != null
      && !latestLostItemFeeAccount.getPaymentStatus().contains("Cancelled")) {

      Collection<Account> filteredAccounts = new ArrayList<>();
      filteredAccounts.add(latestLostItemFeeAccount);
      DateTime creationDate = latestLostItemFeeAccount.getCreationDate();
      Account latestLostItemFeeProcessingAccount = getMostRecentAccount(accounts,
        LOST_ITEM_PROCESSING_FEE_TYPE);

      if (latestLostItemFeeProcessingAccount != null
        && !latestLostItemFeeAccount.getPaymentStatus().contains("Cancelled")
        && Math.abs(secondsBetween(
          creationDate, latestLostItemFeeProcessingAccount.getCreationDate()).getSeconds()) <= 1) {

        filteredAccounts.add(latestLostItemFeeProcessingAccount);
      }
      return filteredAccounts;
    }

    return accounts;
  }

  private Account getMostRecentAccount(Collection<Account> accounts, String lostItemFeeType) {
    return accounts.stream()
      .filter(account -> lostItemFeeType.equals(account.getFeeFineType()))
      .filter(account -> account.getCreationDate() != null)
      .max(Comparator.comparing(Account::getCreationDate))
      .orElse(null);
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
