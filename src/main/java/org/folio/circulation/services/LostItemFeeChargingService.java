package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_FEE_TYPE;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_PROCESSING_FEE_TYPE;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.FeeFineOwnerRepository;
import org.folio.circulation.domain.FeeFineRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.policy.lostitem.ChargeAmountType;
import org.folio.circulation.domain.policy.LostItemPolicyRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostItemFeeChargingService {
  private static final Logger log = LoggerFactory.getLogger(LostItemFeeChargingService.class);
  private static final List<String> FEE_TYPES_TO_RETRIEVE = Arrays.asList(
    LOST_ITEM_FEE_TYPE, LOST_ITEM_PROCESSING_FEE_TYPE);

  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;
  private final FeeFineRepository feeFineRepository;
  private final AccountRepository accountRepository;

  public LostItemFeeChargingService(Clients clients) {
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.feeFineRepository = new FeeFineRepository(clients);
    this.accountRepository = new AccountRepository(clients);
  }

  public CompletableFuture<Result<Loan>> chargeLostItemFees(Result<Loan> loanResult) {
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
      .thenApply(feeFinesResult -> feeFinesResult.next(feeFines -> {
          final LostItemPolicy policy = loan.getLostItemPolicy();
          final List<Result<AccountStorageRepresentation>> accountsToCreate = new ArrayList<>();

          if (shouldChargeItemFee(policy)) {
            log.debug("Charging lost item fee");

            final Result<AccountStorageRepresentation> lostItemFeeResult =
              getFeeFineOfType(feeFines, LOST_ITEM_FEE_TYPE)
                .map(createAccount(loan, owner, policy.getChargeAmountItem().getAmount()));

            accountsToCreate.add(lostItemFeeResult);
          }

          if (shouldChargeProcessingFee(policy)) {
            log.debug("Charging lost item processing fee");

            final Result<AccountStorageRepresentation> processingFeeResult =
              getFeeFineOfType(feeFines, LOST_ITEM_PROCESSING_FEE_TYPE)
                .map(createAccount(loan, owner, policy.getLostItemProcessingFee()));

            accountsToCreate.add(processingFeeResult);
          }

          log.debug("Total accounts created {}", accountsToCreate.size());
          return Result.combineAll(accountsToCreate);
        }
      ));
  }

  private Function<FeeFine, AccountStorageRepresentation> createAccount(
    Loan loan, FeeFineOwner owner, BigDecimal amount) {

    return feeFine -> new AccountStorageRepresentation(loan, loan.getItem(),
      owner, feeFine, amount);
  }

  private boolean shouldChargeItemFee(LostItemPolicy policy) {
    // Set cost fee is only supported now
    return policy.getChargeAmountItem().getChargeType() == ChargeAmountType.SET_COST
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
}
