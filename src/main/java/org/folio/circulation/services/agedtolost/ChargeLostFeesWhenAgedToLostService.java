package org.folio.circulation.services.agedtolost;

import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.representations.LoanProperties.AGED_TO_LOST_DELAYED_BILLING;
import static org.folio.circulation.domain.representations.LoanProperties.DATE_LOST_ITEM_SHOULD_BE_BILLED;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_STATUS;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_HAS_BEEN_BILLED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.lessThanOrEqualTo;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.CommonUtils.pair;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.IdentifierTypeRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.FeeFineFacade;
import org.folio.circulation.services.actualcostrecord.ActualCostRecordService;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

import lombok.val;

public class ChargeLostFeesWhenAgedToLostService {
  private static final Logger log = LogManager.getLogger(ChargeLostFeesWhenAgedToLostService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineFacade feeFineFacade;
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final StoreLoanAndItem storeLoanAndItem;
  private final EventPublisher eventPublisher;
  private final PageableFetcher<Loan> loanPageableFetcher;
  private final FeeFineScheduledNoticeService feeFineScheduledNoticeService;
  private final ActualCostRecordService actualCostRecordService;

  public ChargeLostFeesWhenAgedToLostService(Clients clients,
    ItemRepository itemRepository, UserRepository userRepository) {
    this.itemRepository = itemRepository;
    this.userRepository = userRepository;
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.loanRepository = new LoanRepository(clients, itemRepository,
      userRepository);
    this.storeLoanAndItem = new StoreLoanAndItem(loanRepository,
      itemRepository);
    this.eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    this.loanPageableFetcher = new PageableFetcher<>(loanRepository);
    this.feeFineScheduledNoticeService = FeeFineScheduledNoticeService.using(clients);
    this.actualCostRecordService = new ActualCostRecordService(new ActualCostRecordRepository(clients),
      LocationRepository.using(clients, new ServicePointRepository(clients)),
      new IdentifierTypeRepository(clients), new PatronGroupRepository(clients));
  }

  public CompletableFuture<Result<Void>> chargeFees() {
    log.info("Starting aged to lost items charging...");

    return loanFetchQuery()
      .after(query -> loanPageableFetcher.processPages(query, this::chargeFees));
  }

  public CompletableFuture<Result<Void>> chargeFees(MultipleRecords<Loan> loans) {
    if (loans.isEmpty()) {
      log.info("No aged to lost loans to charge lost fees");
      return ofAsync(() -> null);
    }

    return fetchItemsAndRelatedRecords(loans)
      .thenCompose(r -> r.after(allLoans -> {
        log.info("Loans to charge fees {}", allLoans.size());

        return succeeded(LoanToChargeFees.usingLoans(allLoans))
          .after(this::fetchFeeFineOwners)
          .thenCompose(this::fetchFeeFineTypes)
          .thenCompose(this::chargeLostFeesForLoans);
      }));
  }

  private CompletableFuture<Result<Void>> chargeLostFeesForLoans(
    Result<List<LoanToChargeFees>> loansToChargeFeesResult) {

    return loansToChargeFeesResult
      .after(loans -> allOf(loans, this::chargeLostFees))
      .thenApply(Result::mapEmpty);
  }

  private CompletableFuture<Result<Void>> chargeLostFees(
    LoanToChargeFees loanToChargeFees) {

    return ofAsync(() -> loanToChargeFees)
      .thenCompose(r -> r.after(actualCostRecordService::createIfNecessaryForAgedToLostItem))
      .thenCompose(r -> r.after(this::chargeLostFeesForLoan))
      .thenCompose(r -> r.after(eventPublisher::publishClosedLoanEvent))
      .thenApply(r -> r.mapFailure(failure -> handleFailure(loanToChargeFees, failure.toString())))
      .exceptionally(t -> handleFailure(loanToChargeFees, t.getMessage()));
  }

  private static Result<Void> handleFailure(LoanToChargeFees loan, String errorMessage) {
    log.error("Failed to charge lost item fee(s) for loan {}: {}", loan.getLoanId(), errorMessage);
    return succeeded(null);
  }

  private CompletableFuture<Result<Loan>> chargeLostFeesForLoan(LoanToChargeFees loanToChargeFees) {
    // we can close loans that have no fee to charge
    // and billed immediately
    if (loanToChargeFees.shouldCloseLoan()) {
      log.info("No age to lost fees/fines to charge immediately, closing loan [{}]",
        loanToChargeFees.getLoan().getId());

      return closeLoanAsLostAndPaid(loanToChargeFees);
    }

    Loan loan = loanToChargeFees.getLoan();
    return createAccountsForLoan(loanToChargeFees)
      .after(feeFineFacade::createAccounts)
      .thenCompose(r -> r.after(actions ->
        feeFineScheduledNoticeService.scheduleNoticesForAgedLostFeeFineCharged(loan, actions)))
      .thenCompose(r -> r.after(notUsed -> updateLoanBillingInfo(loanToChargeFees)));
  }

  private Result<List<CreateAccountCommand>> createAccountsForLoan(LoanToChargeFees loanToChargeFees) {
    return validateCanCreateAccountForLoan(loanToChargeFees)
      .map(notUsed -> getChargeableLostFeeToTypePairs(loanToChargeFees)
        .map(pair -> buildCreateAccountCommand(loanToChargeFees, pair))
        .collect(Collectors.toList()));
  }

  private Stream<Pair<AutomaticallyChargeableFee, FeeFine>> getChargeableLostFeeToTypePairs(
    LoanToChargeFees loanToCharge) {

    final LostItemPolicy policy = loanToCharge.getLostItemPolicy();

    val setCostPair = pair(policy.getSetCostFee(), loanToCharge.getLostItemFeeType());
    val processingFeePair = pair(policy.getAgeToLostProcessingFee(),
      loanToCharge.getLostItemProcessingFeeType());

    return Stream.of(setCostPair, processingFeePair)
      .filter(pair -> pair.getKey().isChargeable());
  }

  private CreateAccountCommand buildCreateAccountCommand(LoanToChargeFees loanToCharge,
    Pair<AutomaticallyChargeableFee, FeeFine> pair) {

    final AutomaticallyChargeableFee feeToCharge = pair.getKey();
    final FeeFine feeFineType = pair.getValue();

    return CreateAccountCommand.builder()
      .withAmount(feeToCharge.getAmount())
      .withCreatedByAutomatedProcess(true)
      .withFeeFine(feeFineType)
      .withFeeFineOwner(loanToCharge.getOwner())
      .withLoan(loanToCharge.getLoan())
      .withItem(loanToCharge.getLoan().getItem())
      .withLoanPolicyId(loanToCharge.getLoan().getLoanPolicyId())
      .withOverdueFinePolicyId(loanToCharge.getLoan().getOverdueFinePolicyId())
      .withLostItemFeePolicyId(loanToCharge.getLoan().getLostItemPolicyId())
      .build();
  }

  private CompletableFuture<Result<List<LoanToChargeFees>>> fetchFeeFineTypes(
    Result<List<LoanToChargeFees>> allLoansToChargeResult) {

    return feeFineRepository.getAutomaticFeeFines(lostItemFeeTypes())
      .thenApply(r -> r.combine(allLoansToChargeResult, this::mapFeeFineTypesToLoans));
  }

  private List<LoanToChargeFees> mapFeeFineTypesToLoans(Collection<FeeFine> feeTypes,
    List<LoanToChargeFees> allLoansToCharge) {

    return allLoansToCharge.stream()
      .map(loanToChargeFees -> loanToChargeFees.withFeeFineTypes(feeTypes))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<List<LoanToChargeFees>>> fetchFeeFineOwners(
    List<LoanToChargeFees> allLoansToCharge) {

    final Set<String> primaryServicePointIds = allLoansToCharge.stream()
      .map(LoanToChargeFees::getPrimaryServicePointId)
      .filter(Objects::nonNull)
      .collect(toSet());

    return feeFineOwnerRepository.findOwnersForServicePoints(primaryServicePointIds)
      .thenApply(r -> r.map(owners -> mapOwnersToLoans(owners, allLoansToCharge)));
  }

  private List<LoanToChargeFees> mapOwnersToLoans(Collection<FeeFineOwner> owners,
    List<LoanToChargeFees> loansToCharge) {

    final Map<String, FeeFineOwner> servicePointToOwner = new HashMap<>();

    owners.forEach(owner -> owner.getServicePoints()
      .forEach(servicePoint -> servicePointToOwner.put(servicePoint, owner)));

    return loansToCharge.stream()
      .map(loanToChargeFees -> loanToChargeFees.withOwner(servicePointToOwner))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchItemsAndRelatedRecords(
    MultipleRecords<Loan> loans) {

    return itemRepository.fetchItemsFor(succeeded(loans), Loan::withItem)
      .thenApply(r -> r.next(this::excludeLoansWithNonexistentItems))
      .thenCompose(r -> r.after(userRepository::findUsersForLoans))
      .thenComposeAsync(r -> r.after(lostItemPolicyRepository::findLostItemPoliciesForLoans));
  }

  private Result<MultipleRecords<Loan>> excludeLoansWithNonexistentItems(
    MultipleRecords<Loan> loans) {

    return succeeded(loans.filter(loan -> loan.getItem().isFound()));
  }

  private Result<CqlQuery> loanFetchQuery() {
    final String billingDateProperty = AGED_TO_LOST_DELAYED_BILLING + "."
      + DATE_LOST_ITEM_SHOULD_BE_BILLED;
    final String lostItemHasBeenBilled = AGED_TO_LOST_DELAYED_BILLING + "."
      + LOST_ITEM_HAS_BEEN_BILLED;

    final ZonedDateTime currentDate = getZonedDateTime();

    final Result<CqlQuery> billingDateQuery = lessThanOrEqualTo(billingDateProperty, currentDate);
    final Result<CqlQuery> agedToLostQuery = exactMatch(ITEM_STATUS, AGED_TO_LOST.getValue());
    final Result<CqlQuery> hasNotBeenBilledQuery = exactMatch(
      lostItemHasBeenBilled, "false");

    return billingDateQuery.combine(agedToLostQuery, CqlQuery::and)
      .combine(hasNotBeenBilledQuery, CqlQuery::and)
      .map(query -> query.sortBy(ascending(billingDateProperty)));
  }

  private Result<LoanToChargeFees> validateCanCreateAccountForLoan(LoanToChargeFees loanToChargeFees) {
    if (loanToChargeFees.hasNoFeeFineOwner()) {
      log.warn("No fee/fine owner present for service point {}, skipping loan {}",
        loanToChargeFees.getPrimaryServicePointId(), loanToChargeFees.getLoan().getId());

      return failed(singleValidationError("No fee/fine owner found for item's effective location",
        "servicePointId", loanToChargeFees.getPrimaryServicePointId()));
    }

    final LostItemPolicy lostItemPolicy = loanToChargeFees.getLoan().getLostItemPolicy();

    if (lostItemPolicy.getSetCostFee().isChargeable() && loanToChargeFees.hasNoLostItemFee()) {
      log.warn("No lost item fee type found, skipping loan {}",
        loanToChargeFees.getLoan().getId());

      return failed(singleValidationError("No automated Lost item fee type found",
        "feeFineType", LOST_ITEM_FEE_TYPE));
    }

    if (lostItemPolicy.getAgeToLostProcessingFee().isChargeable()
      && loanToChargeFees.hasNoLostItemProcessingFee()) {

      log.warn("No lost item processing fee type found, skipping loan {}",
        loanToChargeFees.getLoan().getId());

      return failed(singleValidationError("No automated Lost item processing fee type found",
        "feeFineType", LOST_ITEM_PROCESSING_FEE_TYPE));
    }

    return succeeded(loanToChargeFees);
  }

  private CompletableFuture<Result<Loan>> updateLoanBillingInfo(LoanToChargeFees loanToChargeFees) {
    final Loan updatedLoan = loanToChargeFees.getLoan()
      .setLostItemHasBeenBilled()
      .removePreviousAction();

    return loanRepository.updateLoan(updatedLoan);
  }

  private CompletableFuture<Result<Loan>> closeLoanAsLostAndPaid(LoanToChargeFees loanToChargeFees) {
    final Loan loan = loanToChargeFees.getLoan();

    loan.setLostItemHasBeenBilled();
    loan.closeLoanAsLostAndPaid();

    return storeLoanAndItem.updateLoanAndItemInStorage(loan);
  }
}
