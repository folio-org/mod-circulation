package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public abstract class ItemLimitHandlingStrategy {
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);
  private static final String ITEM_BARCODE = "itemBarcode";

  public abstract CompletableFuture<Result<Void>> handle(Loan loan, JsonObject request,
    Clients clients);

  protected CompletableFuture<Result<Boolean>> isLimitReached(Loan loan, Clients clients) {
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    AppliedRuleConditions appliedRuleConditions = loanPolicy.getRuleConditions();

    if (!appliedRuleConditions.isItemTypePresent() && !appliedRuleConditions.isLoanTypePresent()) {
      return ofAsync(() -> false);
    }

    return new LoanRepository(clients)
      .findOpenLoansByUserIdWithItem(loan.getUserId(), LOANS_PAGE_LIMIT)
      .thenApply(mapResult(MultipleRecords::getRecords))
      .thenApply(mapResult(otherLoans -> getMatchingLoansCount(loan, otherLoans)))
      .thenApply(mapResult(loansCount -> loansCount >= loanPolicy.getItemLimit()));
  }

  private long getMatchingLoansCount(Loan loan, Collection<Loan> otherLoans) {
    String materialTypeId = loan.getItem().getMaterialTypeId();
    String loanTypeId = loan.getItem().determineLoanTypeForItem();
    AppliedRuleConditions ruleConditions = loan.getLoanPolicy().getRuleConditions();

    return otherLoans.stream()
      .map(Loan::getItem)
      .filter(not(Item::isClaimedReturned))
      .filter(item -> loanHasMatchingMaterialType(item, materialTypeId, ruleConditions))
      .filter(item -> loanHasMatchingLoanType(item, loanTypeId, ruleConditions))
      .count();
  }

  private boolean loanHasMatchingMaterialType(Item item, String expectedMaterialTypeId,
    AppliedRuleConditions ruleConditions) {

    if (!ruleConditions.isItemTypePresent()) {
      return true;
    }

    return expectedMaterialTypeId != null
      && expectedMaterialTypeId.equals(item.getMaterialTypeId());
  }

  private boolean loanHasMatchingLoanType(Item item, String expectedLoanType,
    AppliedRuleConditions ruleConditions) {

    if (!ruleConditions.isLoanTypePresent()) {
      return true;
    }

    return expectedLoanType != null
      && expectedLoanType.equals(item.determineLoanTypeForItem());
  }

  protected CompletableFuture<Result<Void>> fail(Loan loan, boolean limitIsReached) {
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    String itemBarcode = loan.getItem().getBarcode();

    String errorMessage = String.format("Patron %s reached maximum limit of %d items %s",
      limitIsReached ? "has" : "has not", loanPolicy.getItemLimit(), buildErrorMessage(loanPolicy));

    return completedFuture(failedValidation(errorMessage, ITEM_BARCODE, itemBarcode));
  }

  private String buildErrorMessage(LoanPolicy loanPolicy) {
    AppliedRuleConditions ruleConditions = loanPolicy.getRuleConditions();

    boolean isRuleMaterialTypePresent = ruleConditions.isItemTypePresent();
    boolean isRuleLoanTypePresent = ruleConditions.isLoanTypePresent();
    boolean isRulePatronGroupPresent = ruleConditions.isPatronGroupPresent();

    if (isRulePatronGroupPresent && isRuleMaterialTypePresent && isRuleLoanTypePresent) {
      return "for combination of patron group, material type and loan type";
    } else if (isRulePatronGroupPresent && isRuleMaterialTypePresent) {
      return "for combination of patron group and material type";
    } else if (isRulePatronGroupPresent && isRuleLoanTypePresent) {
      return "for combination of patron group and loan type";
    } else if (isRuleMaterialTypePresent && isRuleLoanTypePresent) {
      return "for combination of material type and loan type";
    } else if (isRuleMaterialTypePresent) {
      return "for material type";
    } else if (isRuleLoanTypePresent) {
      return "for loan type";
    }
    return StringUtils.EMPTY;
  }

  protected static boolean itemLimitIsNotSet(Loan loan) {
    return loan.getLoanPolicy().getItemLimit() == null;
  }

  protected CompletableFuture<Result<Void>> doNothing() {
    return ofAsync(() -> null);
  }
}
