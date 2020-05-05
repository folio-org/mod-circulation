package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.representations.FeeFinePaymentAction.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineRepository;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.services.support.AccountCancellation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeRefundService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeRefundService.class);
  private static final List<String> FEE_TYPES_TO_RETRIEVE = Arrays.asList(
    LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE);

  private final FeeFineRepository feeFineRepository;
  private final FeeFineService feeFineService;
  private final AccountRepository accountRepository;

  public LostItemFeeRefundService(Clients clients) {
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

    return fetchFeeFineTypes(succeeded(context))
      .thenCompose(this::fetchAccountsForLoan)
      .thenCompose(this::processLoanFees)
      .thenApply(r -> r.map(notUsed -> null));
  }

  private CompletableFuture<Result<ReferenceDataContext>> processLoanFees(
    Result<ReferenceDataContext> contextResult) {

    return contextResult.after(context -> {
        return feeFineService.cancelAccounts(getAccountsToCancel(context))
          .thenApply(r -> r.map(notUsed -> context));
    });
  }

  private List<AccountCancellation> getAccountsToCancel(ReferenceDataContext context) {
    return context.accounts.stream()
      .filter(Account::isOpen)
      .filter(account -> account.getRemaining().compareTo(BigDecimal.ZERO) > 0)
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
    Result<ReferenceDataContext> contextResult) {

    return contextResult.combineAfter(
      context -> feeFineRepository.getAutomaticFeeFines(FEE_TYPES_TO_RETRIEVE),
      ReferenceDataContext::withFeeFines);
  }

  private static final class ReferenceDataContext {
    private final ItemStatus itemStatus;
    private final Loan loan;
    private final String staffUserId;
    private final String servicePointId;
    private Collection<Account> accounts;
    private Collection<FeeFine> feeFines;

    public ReferenceDataContext(ItemStatus itemStatus, Loan loan, String staffUserId,
      String servicePointId) {

      this.itemStatus = itemStatus;
      this.loan = loan;
      this.staffUserId = staffUserId;
      this.servicePointId = servicePointId;
    }

    public ReferenceDataContext withFeeFines(Collection<FeeFine> feeFines) {
      this.feeFines = feeFines;
      return this;
    }

    public ReferenceDataContext withAccounts(Collection<Account> accounts) {
      this.accounts = accounts;
      return this;
    }

    public Set<String> getFeeFineIds() {
      return feeFines.stream()
        .map(FeeFine::getId)
        .collect(Collectors.toSet());
    }
  }
}
