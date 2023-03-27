package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeRefundContext.forCheckIn;
import static org.folio.circulation.services.LostItemFeeRefundContext.forRenewal;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.logging.LogHelper.asString;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
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
import org.folio.circulation.support.utils.DateTimeUtil;

public class LostItemFeeRefundService {
  private static final Logger log = LogManager.getLogger(LostItemFeeRefundService.class);
  private static final String CANCELLED_PAYMENT_STATUS_PREFIX = "Cancelled";
  public static final int MAX_TIME_DIFFERENCE_FOR_ASSOCIATED_ACCOUNTS = 60;

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineFacade feeFineFacade;
  private final AccountRepository accountRepository;
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final ItemRepository itemRepository;
  private final ActualCostRecordRepository actualCostRecordRepository;
  private final FeeFineScheduledNoticeService scheduledNoticeService;

  public LostItemFeeRefundService(Clients clients,
    ItemRepository itemRepository, UserRepository userRepository,
    LoanRepository loanRepository) {

    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.accountRepository = new AccountRepository(clients);
    this.loanRepository = loanRepository;
    this.scheduledNoticeService = FeeFineScheduledNoticeService.using(clients);
    this.actualCostRecordRepository = new ActualCostRecordRepository(clients);
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

  /**
   * Refund lost item fee(s) and cancel the actual cost record associated with the loan
   * when requirements for a refund are met
   * @param refundFeeContext an aggregate object with all the data required to perform a refund
   * @return refund context wrapped in an asynchronous result
   */
  public CompletableFuture<Result<LostItemFeeRefundContext>> refundLostItemFees(
    LostItemFeeRefundContext refundFeeContext) {

    log.info("refundLostItemFees:: attempting to refund lost item fees: loanId={}, cancelReason={}",
      refundFeeContext::getLoanId, refundFeeContext::getCancelReason);

    if (!refundFeeContext.shouldRefundFeesForItem()) {
      log.info("refundLostItemFees:: no need to refund fees for loan {}", refundFeeContext::getLoanId);
      return completedFuture(succeeded(refundFeeContext));
    }

    return lookupLoan(succeeded(refundFeeContext))
      .thenCompose(this::fetchLostItemPolicy)
      .thenCompose(r -> r.after(this::processRefund))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  protected CompletableFuture<Result<LostItemFeeRefundContext>> processRefund(
    LostItemFeeRefundContext context) {

    log.debug("processRefund:: context={}", context);

    if (!context.getLostItemPolicy().shouldRefundFees(context.getItemLostDate())) {
      log.info("processRefund:: refund interval was exceeded for loan {}", context::getLoanId);
      return ofAsync(context);
    }

    return fetchAccountsAndActionsForLoan(context)
      .thenCompose(r -> r.after(this::refundAccounts))
      .thenCompose(r -> r.after(this::cancelActualCostFee))
      .thenApply(r -> r.map(ignored -> context));
  }

  public CompletableFuture<Result<LostItemFeeRefundContext>> refundAccounts(
    LostItemFeeRefundContext context) {

    log.debug("refundAccounts:: context={}", context);
    Collection<Account> accounts = context.getLoan().getAccounts();

    if (isEmpty(accounts)) {
      log.info("refundAccounts:: no accounts to refund for loan {}", context.getLoanId());
      return ofAsync(context);
    }

    log.info("refundAccounts:: refunding {} accounts for loan {}: {}", accounts::size,
      context::getLoanId, () -> asString(accounts, Account::getId));

    return allOf(context.accountRefundCommands(), command -> refundAndCloseAccount(context, command))
      .thenApply(r -> r.map(ignored -> context));
  }

  private CompletableFuture<Result<ActualCostRecord>> cancelActualCostFee(
    LostItemFeeRefundContext context) {

    log.info("cancelActualCostFee:: attempting to find and cancel actual cost fee for loan {}",
      context::getLoanId);

    if (!context.getLostItemPolicy().hasActualCostFee()) {
      log.info("cancelActualCostFee:: lost item fee policy {} has no actual cost fee configured",
        context.getLostItemPolicy().getId());
      return emptyAsync();
    }

    return actualCostRecordRepository.findMostRecentOpenRecordForLoan(context.getLoan())
      .thenCompose(r -> r.after(rec -> feeFineFacade.cancelActualCostFee(rec,
        context.getActualCostFeeCancelReason())));
  }

  private CompletableFuture<Result<AccountActionResponse>> refundAndCloseAccount(
    LostItemFeeRefundContext context, RefundAndCancelAccountCommand command) {

    return userRepository.getUser(command.getStaffUserId())
      .thenCompose(r -> r.after(user -> processAccount(context, command, user)));
  }

  private CompletableFuture<Result<AccountActionResponse>> processAccount(
    LostItemFeeRefundContext context, RefundAndCancelAccountCommand command, User user) {

    return fromFutureResult(feeFineFacade.refundAccountIfNeeded(command, user))
      .onSuccess(refundResponse -> schedulePatronNotices(context, refundResponse))
      .flatMapFuture(refundResponse -> feeFineFacade.cancelAccountIfNeeded(command, user, refundResponse))
      .onSuccess(cancelResponse -> schedulePatronNotices(context, cancelResponse))
      .toCompletableFuture();
  }

  private void schedulePatronNotices(LostItemFeeRefundContext context,
    AccountActionResponse response) {

    if (shouldSchedulePatronNotices(context, response)) {
      response.getFeeFineActions().stream()
        // no patron notices should be created for "credit" actions
        .filter(not(FeeFineAction::isCredited))
        .forEach(ffa -> scheduledNoticeService.scheduleAgedToLostReturnedNotices(context, ffa));
    }
  }

  private static boolean shouldSchedulePatronNotices(LostItemFeeRefundContext context,
    AccountActionResponse response) {

    return context.getCancelReason() == CANCELLED_ITEM_RETURNED
      && response != null
      && isNotEmpty(response.getFeeFineActions());
  }

  protected CompletableFuture<Result<LostItemFeeRefundContext>> lookupLoan(
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
    if(context.getLostItemPolicy().hasActualCostFee()) {
      feeFineTypes.add(LOST_ITEM_ACTUAL_COST_FEE_TYPE);
    }
    else {
      feeFineTypes.add(LOST_ITEM_FEE_TYPE);
    }

    if (context.getLostItemPolicy().isRefundProcessingFeeWhenReturned()) {
      feeFineTypes.add(LOST_ITEM_PROCESSING_FEE_TYPE);
    }

    final Result<CqlQuery> fetchQuery = exactMatch("loanId", context.getLoanId())
      .combine(exactMatchAny("feeFineType", feeFineTypes), CqlQuery::and);

      return accountRepository.findAccountsAndActionsForLoanByQuery(fetchQuery)
        .thenCompose(r -> r.after(accounts -> filterAccountsForRefund(accounts, feeFineTypes)))
        .thenApply(r -> r.map(context::withAccounts));
  }

  private CompletableFuture<Result<Collection<Account>>> filterAccountsForRefund(
    Collection<Account> accounts, List<String> feeFineTypes) {

    if (accounts == null || accounts.isEmpty()) {
      return ofAsync(Collections::emptyList);
    }

    return ofAsync(() -> getLatestAccount(accounts, feeFineTypes))
      .thenCompose(r -> r.after(this::setActualCostRecordCreationDate))
      .thenApply(r -> r.map(account -> findRefundableAccounts(account, accounts, feeFineTypes)));
  }

  private CompletableFuture<Result<Account>> setActualCostRecordCreationDate(
    Account latestAccount) {

    if (latestAccount == null || !LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals(
      latestAccount.getFeeFineType())) {

      return ofAsync(() -> latestAccount);
    }

    return succeeded(latestAccount)
      .combineAfter(account -> actualCostRecordRepository.getActualCostRecordByAccountId(
        latestAccount.getId()), (account, actualCostRecord) -> account
          .withActualRecordCreationDate(actualCostRecord.getCreationDate()));
  }

  private List<Account> findRefundableAccounts(Account latestAccount,
    Collection<Account> accounts, List<String> feeFineTypes) {

    List<Account> accountsForRefund = new ArrayList<>();
    accountsForRefund.add(latestAccount);
    List<String> feeFineTypeForAssociatedAccount = feeFineTypes.stream()
      .filter(not(latestAccount.getFeeFineType()::equals))
      .collect(Collectors.toList());

    Optional.ofNullable(getLatestAccount(accounts, feeFineTypeForAssociatedAccount))
      .filter(this::isAccountEligibleForRefund)
      .filter(associatedAccount -> isTimeDifferenceWithAssociatedAccountSuitable(
        latestAccount, associatedAccount))
      .ifPresentOrElse(accountsForRefund::add, () -> log.debug("No refundable accounts found"));

    return accountsForRefund;
  }

  private boolean isAccountEligibleForRefund(Account latestLostItemFeeAccount) {
    return latestLostItemFeeAccount.getPaymentStatus() != null
      && !latestLostItemFeeAccount.getPaymentStatus().startsWith(CANCELLED_PAYMENT_STATUS_PREFIX)
      && latestLostItemFeeAccount.getCreationDate() != null;
  }

  private boolean isTimeDifferenceWithAssociatedAccountSuitable(Account latestAccount,
    Account associatedAccount) {

    if (LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals(latestAccount.getFeeFineType())) {
      return isDifferenceOneMinuteOrLess(latestAccount.getActualRecordCreationDate(),
        associatedAccount.getCreationDate());
    }

    return isDifferenceOneMinuteOrLess(latestAccount.getCreationDate(),
      associatedAccount.getCreationDate());
  }

  private boolean isDifferenceOneMinuteOrLess(ZonedDateTime firstDate,
    ZonedDateTime secondDate) {

    return DateTimeUtil.getSecondsBetween(firstDate, secondDate)
      <= MAX_TIME_DIFFERENCE_FOR_ASSOCIATED_ACCOUNTS;
  }

  private Account getLatestAccount(Collection<Account> accounts, List<String> feeFineTypes) {
    return accounts.stream()
      .filter(account -> feeFineTypes.contains(account.getFeeFineType()))
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
