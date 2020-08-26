package org.folio.circulation.services.agedtolost;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.lostItemFeeTypes;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.representations.LoanProperties.AGED_TO_LOST_DELAYED_BILLING;
import static org.folio.circulation.domain.representations.LoanProperties.DATE_LOST_ITEM_SHOULD_BE_BILLED;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_STATUS;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_HAS_BEEN_BILLED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.lessThanOrEqualTo;
import static org.folio.circulation.support.http.client.PageLimit.oneThousand;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.CommonUtils.pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.policy.lostitem.itemfee.AutomaticallyChargeableFee;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.services.FeeFineFacade;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.val;

public class AssignLostFeesWhenAgedToLostService {
  private static final Logger log = LoggerFactory.getLogger(AssignLostFeesWhenAgedToLostService.class);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final FeeFineFacade feeFineFacade;
  private final LoanRepository loanRepository;
  private final ItemRepository itemRepository;

  public AssignLostFeesWhenAgedToLostService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.feeFineFacade = new FeeFineFacade(clients);
    this.loanRepository = new LoanRepository(clients);
    this.itemRepository = new ItemRepository(clients, true, false, false);
  }

  public CompletableFuture<Result<Void>> processFeeAssigning() {
    return fetchLoansAndItems()
      .thenCompose(r -> r.after(allLoans -> {
        if (allLoans.isEmpty()) {
          log.info("No aged to lost loans to assign lost fees");
          return completedFuture(succeeded(null));
        }

        log.debug("Loans to assign fees [{}]", allLoans.getRecords().size());

        return succeeded(LoanToAssignFees.usingLoans(allLoans))
          .after(this::fetchFeeFineOwners)
          .thenComposeAsync(this::fetchFeeFineTypes)
          .thenCompose(this::assignLostFeesForLoans);
      }));
  }

  private CompletableFuture<Result<Void>> assignLostFeesForLoans(
    Result<List<LoanToAssignFees>> loansToAssignFeesResult) {

    return loansToAssignFeesResult
      .after(loansToAssignFees -> allOf(loansToAssignFees, this::assignLostFeesForLoan))
      .thenApply(r -> r.map(notUsed -> null));
  }

  private CompletableFuture<Result<Loan>> assignLostFeesForLoan(LoanToAssignFees loanToAssignFees) {
    return createAccountsForLoan(loanToAssignFees)
      .after(feeFineFacade::createAccounts)
      .thenCompose(r -> r.after(notUsed -> updateLoanBillingInfo(loanToAssignFees)));
  }

  private CompletableFuture<Result<Loan>> updateLoanBillingInfo(LoanToAssignFees loanToAssignFees) {
    final Loan updatedLoan = loanToAssignFees.getLoan().setLostItemHasBeenBilled();

    return loanRepository.updateLoan(updatedLoan);
  }

  private Result<List<CreateAccountCommand>> createAccountsForLoan(LoanToAssignFees loanToAssignFees) {
    return validateCanCreateAccountForLoan(loanToAssignFees)
      .map(notUsed -> getChargeableLostFeeToTypePairs(loanToAssignFees)
        .map(pair -> buildCreateAccountCommand(loanToAssignFees, pair))
        .collect(Collectors.toList()));
  }

  private Stream<Pair<AutomaticallyChargeableFee, FeeFine>> getChargeableLostFeeToTypePairs(
    LoanToAssignFees loan) {

    final LostItemPolicy policy = loan.getLostItemPolicy();

    val setCostPair = pair(policy.getSetCostFee(), loan.getLostItemFeeType());
    val processingFeePair = pair(policy.getProcessingFee(), loan.getLostItemProcessingFeeType());

    return Stream.of(setCostPair, processingFeePair)
      .filter(pair -> pair.getKey().isChargeable());
  }

  private CreateAccountCommand buildCreateAccountCommand(LoanToAssignFees loan,
    Pair<AutomaticallyChargeableFee, FeeFine> pair) {

    final AutomaticallyChargeableFee feeToCharge = pair.getKey();
    final FeeFine feeFineType = pair.getValue();

    return CreateAccountCommand.builder()
      .withAmount(feeToCharge.getAmount())
      .withCreatedByAutomatedProcess(true)
      .withFeeFine(feeFineType)
      .withFeeFineOwner(loan.getOwner())
      .withLoan(loan.getLoan())
      .withItem(loan.getLoan().getItem())
      .build();
  }

  private CompletableFuture<Result<List<LoanToAssignFees>>> fetchFeeFineTypes(
    Result<List<LoanToAssignFees>> allLoansResult) {

    return feeFineRepository.getAutomaticFeeFines(lostItemFeeTypes())
      .thenApply(r -> r.combine(allLoansResult, this::mapFeeFineTypesToLoans));
  }

  private List<LoanToAssignFees> mapFeeFineTypesToLoans(Collection<FeeFine> feeTypes,
     List<LoanToAssignFees> allLoans) {

    return allLoans.stream()
      .map(loanToAssignFees -> loanToAssignFees.withFeeFineTypes(feeTypes))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<List<LoanToAssignFees>>> fetchFeeFineOwners(
    List<LoanToAssignFees> allLoans) {

    final Set<String> uniqueEffectiveLocationServicePoints = allLoans.stream()
      .map(LoanToAssignFees::getOwnerServicePointId)
      .collect(Collectors.toSet());

    return feeFineOwnerRepository.findOwnersForServicePoints(uniqueEffectiveLocationServicePoints)
      .thenApply(r -> r.map(owners -> mapOwnersToLoans(owners, allLoans)));
  }

  private List<LoanToAssignFees> mapOwnersToLoans(Collection<FeeFineOwner> owners,
    List<LoanToAssignFees> loans) {

    final Map<String, FeeFineOwner> servicePointToOwner = new HashMap<>();

    owners.forEach(owner -> owner.getServicePoints()
      .forEach(servicePoint -> servicePointToOwner.put(servicePoint, owner)));

    return loans.stream()
      .map(loanToAssignFees -> loanToAssignFees.withOwner(servicePointToOwner))
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchLoansAndItems() {
    return loanFetchQuery()
      .after(query -> loanRepository.findByQuery(query, oneThousand()))
      .thenComposeAsync(loansResult -> itemRepository.fetchItemsFor(loansResult, Loan::withItem))
      .thenComposeAsync(r -> r.after(lostItemPolicyRepository::findLostItemPoliciesForLoans));
  }

  private Result<CqlQuery> loanFetchQuery() {
    final String billingDateProperty = AGED_TO_LOST_DELAYED_BILLING + "."
      + DATE_LOST_ITEM_SHOULD_BE_BILLED;
    final String lostItemHasBeenBilled = AGED_TO_LOST_DELAYED_BILLING + "."
      + LOST_ITEM_HAS_BEEN_BILLED;

    final DateTime currentDate = getClockManager().getDateTime();

    final Result<CqlQuery> billingDateQuery = lessThanOrEqualTo(billingDateProperty, currentDate);
    final Result<CqlQuery> agedToLostQuery = exactMatch(ITEM_STATUS, AGED_TO_LOST.getValue());
    final Result<CqlQuery> hasNotBeenBilledQuery = exactMatch(
      lostItemHasBeenBilled, "false");

    return Result.combine(billingDateQuery, agedToLostQuery, CqlQuery::and)
      .combine(hasNotBeenBilledQuery, CqlQuery::and)
      .map(query -> query.sortBy(ascending(billingDateProperty)));
  }

  private Result<LoanToAssignFees> validateCanCreateAccountForLoan(LoanToAssignFees loanToAssignFees) {
    if (loanToAssignFees.hasNoFeeFineOwner()) {
      log.warn("No fee/fine owner present for service point {}, skipping loan {}",
        loanToAssignFees.getOwnerServicePointId(), loanToAssignFees.getLoan().getId());

      return failed(singleValidationError("No fee/fine owner found for item's effective location",
        "servicePointId", loanToAssignFees.getOwnerServicePointId()));
    }

    final LostItemPolicy lostItemPolicy = loanToAssignFees.getLoan().getLostItemPolicy();

    if (lostItemPolicy.getSetCostFee().isChargeable() && loanToAssignFees.hasNoLostItemFee()) {
      log.warn("No lost item fee type found, skipping loan {}",
        loanToAssignFees.getLoan().getId());

      return failed(singleValidationError("No automated Lost item fee found",
        "feeFineType", LOST_ITEM_FEE_TYPE));
    }

    if (lostItemPolicy.getAgeToLostProcessingFee().isChargeable()
      && loanToAssignFees.hasNoLostItemProcessingFee()) {

      log.warn("No lost item processing fee type found, skipping loan {}",
        loanToAssignFees.getLoan().getId());

      return failed(singleValidationError("No automated Lost item processing fee found",
        "feeFineType", LOST_ITEM_PROCESSING_FEE_TYPE));
    }

    return succeeded(loanToAssignFees);
  }
}
