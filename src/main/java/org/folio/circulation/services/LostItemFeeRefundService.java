package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.representations.FeeFinePaymentAction.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineRepository;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.services.support.AccountCancellation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeRefundService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeRefundService.class);
  private static final List<String> FEE_TYPES_TO_RETRIEVE = Arrays.asList(
    LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineService feeFineService;
  private final AccountRepository accountRepository;

  public LostItemFeeRefundService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineService = new FeeFineService(clients);
    this.accountRepository = new AccountRepository(clients);
  }

  public CompletableFuture<Result<CheckInProcessRecords>> refundLostItemFees(
    CheckInProcessRecords checkInRecords) {

    final ReferenceDataContext referenceDataContext = new ReferenceDataContext(
      checkInRecords.getItemStatusBeforeCheckIn(), checkInRecords.getLoan(),
      checkInRecords.getLoggedInUserId(), checkInRecords.getCheckInServicePointId().toString());

    return refundLostItemFees(referenceDataContext)
      .thenApply(r -> r.map(notUsed -> checkInRecords));
  }

  private CompletableFuture<Result<Void>> refundLostItemFees(ReferenceDataContext context) {
    if (context.itemStatus != ItemStatus.DECLARED_LOST) {
      return completedFuture(succeeded(null));
    }

    return fetchLostItemPolicy(succeeded(context))
      .thenCompose(contextResult -> contextResult.after(refData -> {
        if (shouldNotRefundFees(refData)) {
          log.debug("Refund interval has exceeded for loan [{}]", refData.loan.getId());
          return completedFuture(succeeded(null));
        }

        return fetchFeeFineTypes(refData)
          .thenCompose(this::fetchAccountsForLoan)
          .thenCompose(this::processLoanFees)
          .thenApply(r -> r.map(notUsed -> null));
      }));
  }

  private CompletableFuture<Result<ReferenceDataContext>> processLoanFees(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.after(context -> {
      final Collection<Account> accountsToRefund = getAccountsToRefund(context);

      return feeFineService.cancelAccounts(getAccountsToCancel(accountsToRefund, context))
        .thenApply(r -> r.map(notUsed -> context));
    });
  }

  private Collection<Account> getAccountsToRefund(ReferenceDataContext context) {
    if (context.lostItemPolicy.isRefundProcessingFeeWhenReturned()) {
      return context.accounts;
    }

    return context.accounts.stream()
      .filter(account -> !account.getFeeFineId().equals(context.lostItemProcessingFee.getId()))
      .collect(Collectors.toList());
  }

  private List<AccountCancellation> getAccountsToCancel(Collection<Account> accounts,
    ReferenceDataContext context) {

    return accounts.stream()
      .filter(Account::isOpen)
      .filter(Account::hasRemainingAmount)
      .map(account -> AccountCancellation.builder()
        .withAccountToCancel(account)
        .withCancellationReason(CANCELLED_ITEM_RETURNED)
        .withStaffUserId(context.staffUserId)
        .withServicePointId(context.servicePointId)
        .build())
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchAccountsForLoan(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.after(context -> {
      final Result<CqlQuery> fetchQuery = exactMatch("loanId", context.loan.getId())
        .combine(exactMatchAny("feeFineId", context.getFeeFineIds()), CqlQuery::and);

      return accountRepository.findAccounts(fetchQuery)
        .thenApply(r -> r.map(context::withAccounts));
    });
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchFeeFineTypes(
    ReferenceDataContext context) {

    return feeFineRepository.getAutomaticFeeFines(FEE_TYPES_TO_RETRIEVE)
      .thenApply(r -> r.next(
        feeFines -> getFeeFineOfType(feeFines, LOST_ITEM_FEE_TYPE)
          .map(context::withLostItemFee)
          .next(notUsed -> getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE_TYPE))
          .map(context::withLostItemProcessingFee)
      ));
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchLostItemPolicy(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> lostItemPolicyRepository.getLostItemPolicyById(context.loan.getLostItemPolicyId()),
      ReferenceDataContext::withLostItemPolicy);
  }

  private boolean shouldNotRefundFees(ReferenceDataContext context) {
    final TimePeriod feeRefundInterval = context.lostItemPolicy.getFeeRefundInterval();
    final DateTime now = ClockManager.getClockManager().getDateTime();
    final DateTime declareLostDateTime = context.loan.getDeclareLostDateTime();

    return feeRefundInterval != null && feeRefundInterval
      .between(declareLostDateTime, now) > feeRefundInterval.getDuration();
  }

  private Result<FeeFine> getFeeFineOfType(Collection<FeeFine> feeFines, String type) {
    return feeFines.stream()
      .filter(feeFine -> feeFine.getFeeFineType().equals(type))
      .findFirst()
      .map(Result::succeeded)
      .orElse(createFeeFineNotFoundResult(type));
  }

  private Result<FeeFine> createFeeFineNotFoundResult(String type) {
    return failed(singleValidationError("Expected automated fee of type " + type,
      "feeFineType", type));
  }

  private static final class ReferenceDataContext {
    private final ItemStatus itemStatus;
    private final Loan loan;
    private final String staffUserId;
    private final String servicePointId;
    private Collection<Account> accounts;
    private FeeFine lostItemFee;
    private FeeFine lostItemProcessingFee;
    private LostItemPolicy lostItemPolicy;

    public ReferenceDataContext(ItemStatus itemStatus, Loan loan, String staffUserId,
      String servicePointId) {

      this.itemStatus = itemStatus;
      this.loan = loan;
      this.staffUserId = staffUserId;
      this.servicePointId = servicePointId;
    }

    public ReferenceDataContext withLostItemFee(FeeFine lostItemFee) {
      this.lostItemFee = lostItemFee;
      return this;
    }

    public ReferenceDataContext withLostItemProcessingFee(FeeFine lostItemProcessingFee) {
      this.lostItemProcessingFee = lostItemProcessingFee;
      return this;
    }

    public ReferenceDataContext withAccounts(Collection<Account> accounts) {
      this.accounts = accounts;
      return this;
    }

    public ReferenceDataContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
      this.lostItemPolicy = lostItemPolicy;
      return this;
    }

    public List<String> getFeeFineIds() {
      return Arrays.asList(lostItemFee.getId(), lostItemProcessingFee.getId());
    }
  }
}
