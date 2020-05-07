package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.support.Result.succeeded;
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
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.services.support.AccountRefund;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeRefundService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeRefundService.class);
  private static final List<String> LOST_ITEM_FEE_TYPES = Arrays.asList(
    LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineService feeFineService;
  private final AccountRepository accountRepository;

  public LostItemFeeRefundService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
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
        if (refData.lostItemPolicy.shouldRefundFees(refData.loan.getDeclareLostDateTime())) {
          log.debug("Refund interval has exceeded for loan [{}]", refData.loan.getId());
          return completedFuture(succeeded(null));
        }

        return fetchAccountsForLoan(contextResult)
          .thenCompose(r -> r.after(notUsed -> feeFineService
            .refundAndCloseAccounts(getAccountsToRefund(context))))
          .thenApply(r -> r.map(notUsed -> null));
      }));
  }

  private List<AccountRefund> getAccountsToRefund(ReferenceDataContext context) {
    Collection<Account> allAccounts = context.accounts;

    if (!context.lostItemPolicy.isRefundProcessingFeeWhenReturned()) {
      allAccounts = context.accounts.stream()
        .filter(account -> !account.getFeeFineType().equals(LOST_ITEM_PROCESSING_FEE_TYPE))
        .collect(Collectors.toList());
    }

    return allAccounts.stream()
      .map(account -> new AccountRefund(account, context.staffUserId, context.servicePointId))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchAccountsForLoan(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.after(context -> {
      final Result<CqlQuery> fetchQuery = exactMatch("loanId", context.loan.getId())
        .combine(exactMatchAny("feeFineType", LOST_ITEM_FEE_TYPES), CqlQuery::and);

      return accountRepository.findAccounts(fetchQuery)
        .thenApply(r -> r.map(context::withAccounts));
    });
  }

  private CompletableFuture<Result<ReferenceDataContext>> fetchLostItemPolicy(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> lostItemPolicyRepository.getLostItemPolicyById(context.loan.getLostItemPolicyId()),
      ReferenceDataContext::withLostItemPolicy);
  }

  private static final class ReferenceDataContext {
    private final ItemStatus itemStatus;
    private final Loan loan;
    private final String staffUserId;
    private final String servicePointId;
    private Collection<Account> accounts;
    private LostItemPolicy lostItemPolicy;

    public ReferenceDataContext(ItemStatus itemStatus, Loan loan, String staffUserId,
      String servicePointId) {

      this.itemStatus = itemStatus;
      this.loan = loan;
      this.staffUserId = staffUserId;
      this.servicePointId = servicePointId;
    }

    public ReferenceDataContext withAccounts(Collection<Account> accounts) {
      this.accounts = accounts;
      return this;
    }

    public ReferenceDataContext withLostItemPolicy(LostItemPolicy lostItemPolicy) {
      this.lostItemPolicy = lostItemPolicy;
      return this;
    }
  }
}
