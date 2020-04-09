package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.FeeFineOwnerRepository;
import org.folio.circulation.domain.FeeFineRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.LostItemPolicy;
import org.folio.circulation.domain.policy.LostItemPolicyChargeAmountType;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclaredLostFeeFineService {
  private static final Logger log = LoggerFactory.getLogger(DeclaredLostFeeFineService.class);
  private static final List<String> FEE_TYPES_TO_RETRIEVE = Arrays.asList(
    LOST_ITEM_FEE, LOST_ITEM_PROCESSING_FEE);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final AccountRepository accountRepository;

  public DeclaredLostFeeFineService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.accountRepository = new AccountRepository(clients);
  }

  public CompletableFuture<Result<Loan>> chargeFeeFines(Result<Loan> loanResult) {
    return lostItemPolicyRepository.findLostItemPolicyForLoan(loanResult)
      .thenCompose(loanWithPolicyResult -> loanWithPolicyResult.after(loan -> {
        if (!shouldChargeAnyFee(loan.getLostItemPolicy())) {
          log.debug("No fee is going to be charged, skipping logic, loan id {}", loan.getId());
          return completedFuture(succeeded(loan));
        }

        return getFeeFineOwner(loan)
          .thenCompose(r -> r.after(owner -> buildAccountsForPatron(owner, loan)))
          .thenCompose(r -> r.after(accountRepository::createAll))
          .thenApply(r -> r.map(notUsed -> loan));
      }));
  }

  private CompletableFuture<Result<List<AccountStorageRepresentation>>> buildAccountsForPatron(
    final FeeFineOwner owner, Loan loan) {

    if (owner == null) {
      log.warn("Can not find owner for service point {}, no fee will be charged",
        loan.getItem().getLocation().getPrimaryServicePointId());

      return completedFuture(succeeded(Collections.emptyList()));
    }

    return feeFineRepository.getAutomaticFeeFines(FEE_TYPES_TO_RETRIEVE)
      .thenApply(result -> refuseWhenFeeFineOfTypeNotFound(result, LOST_ITEM_FEE))
      .thenApply(result -> refuseWhenFeeFineOfTypeNotFound(result, LOST_ITEM_PROCESSING_FEE))
      .thenApply(feeFinesResult -> feeFinesResult.next(feeFines -> {
          final LostItemPolicy policy = loan.getLostItemPolicy();
          final List<AccountStorageRepresentation> accountsToCreate = new ArrayList<>();

          final FeeFine lostItemFee = getFeeFineOfType(feeFines, LOST_ITEM_FEE);
          final FeeFine lostItemProcessingFee = getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE);

          if (shouldChargeItemFee(policy)) {
            log.debug("Charging lost item fee");

            accountsToCreate.add(new AccountStorageRepresentation(loan, loan.getItem(),
              owner, lostItemFee, policy.getChargeAmountItem().getAmount()));
          }

          if (shouldChargeProcessingFee(policy)) {
            log.debug("Charging lost item processing fee");

            accountsToCreate.add(new AccountStorageRepresentation(loan, loan.getItem(),
              owner, lostItemProcessingFee, policy.getLostItemProcessingFee()));
          }

          log.debug("Total accounts created {}", accountsToCreate.size());

          return succeeded(accountsToCreate);
        }
      ));
  }

  private boolean shouldChargeItemFee(LostItemPolicy policy) {
    // Set cost fee is only supported now
    return policy.getChargeAmountItem().getChargeType() == LostItemPolicyChargeAmountType.SET_COST
      && isGreaterThanZero(policy.getChargeAmountItem().getAmount());
  }

  private boolean shouldChargeProcessingFee(LostItemPolicy policy) {
    return policy.isChargeAmountItemPatron()
      && isGreaterThanZero(policy.getLostItemProcessingFee());
  }

  private boolean shouldChargeAnyFee(LostItemPolicy policy) {
    return shouldChargeItemFee(policy) || shouldChargeProcessingFee(policy);
  }

  private boolean isGreaterThanZero(BigDecimal numberToCompare) {
    return numberToCompare != null && numberToCompare.compareTo(BigDecimal.ZERO) > 0;
  }

  private CompletableFuture<Result<FeeFineOwner>> getFeeFineOwner(Loan loan) {
    final String servicePointId = loan.getItem().getLocation()
      .getPrimaryServicePointId().toString();

    return feeFineOwnerRepository.findOwnerForServicePoint(servicePointId);
  }

  private FeeFine getFeeFineOfType(Collection<FeeFine> feeFines, String type) {
    return feeFines.stream()
      .filter(feeFine -> feeFine.getFeeFineType().equals(type))
      .findFirst()
      .orElse(null);
  }

  private Result<Collection<FeeFine>> refuseWhenFeeFineOfTypeNotFound(
    Result<Collection<FeeFine>> feeFinesResult, String type) {

    return feeFinesResult.failWhen(
      feeFines -> Result.succeeded(getFeeFineOfType(feeFines, type) == null),
      feeFines -> singleValidationError("Expected automated fee of type " + type,
        "feeFineType", type));
  }
}
