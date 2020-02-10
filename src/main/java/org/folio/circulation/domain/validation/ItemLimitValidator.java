package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.PageLimit;

public class ItemLimitValidator {
  private final Function<String, ValidationErrorFailure> itemLimitErrorFunction;
  private final LoanRepository loanRepository;
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);

  public ItemLimitValidator(Function<String, ValidationErrorFailure> itemLimitErrorFunction,
    LoanRepository loanRepository) {

    this.itemLimitErrorFunction = itemLimitErrorFunction;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemLimitIsReached(
    LoanAndRelatedRecords records) {

    Loan loan = records.getLoan();
    Integer itemLimit = loan.getLoanPolicy().getItemLimit();

    if (itemLimit == null) {
      return completedFuture(succeeded(records));
    }

    return ofAsync(() -> loan.getLoanPolicy().getRuleConditions())
      .thenComposeAsync(result -> result.failAfter(ruleConditions -> isLimitReached(ruleConditions, records),
        ruleConditions -> {
          String message = getErrorMessage(ruleConditions);
          return itemLimitErrorFunction.apply(String.format("Patron has reached maximum limit of %d items %s",
            itemLimit, message));
        }))
      .thenApply(result -> result.map(v -> records));
  }

  private CompletableFuture<Result<Boolean>> isLimitReached(
    AppliedRuleConditions ruleConditionsEntity, LoanAndRelatedRecords records) {

    if (!ruleConditionsEntity.isItemTypePresent() && !ruleConditionsEntity.isLoanTypePresent()) {
      return ofAsync(() -> false);
    }

    Item item = records.getLoan().getItem();
    String materialTypeId = item.getMaterialType() != null
      ? item.getMaterialTypeId()
      : null;
    String loanTypeId = item.determineLoanTypeForItem();
    Integer itemLimit = records.getLoan().getLoanPolicy().getItemLimit();
    AppliedRuleConditions ruleConditions = records.getLoan().getLoanPolicy().getRuleConditions();

    return loanRepository.findOpenLoansByUserIdWithItem(LOANS_PAGE_LIMIT, records)
      .thenApply(r -> r.map(loanRecords -> loanRecords.getRecords().stream()
        .filter(loanRecord -> isMaterialTypeMatchInRetrievedLoan(
          materialTypeId, loanRecord, ruleConditions))
        .filter(loanRecord -> isLoanTypeMatchInRetrievedLoan(
          loanTypeId, loanRecord, ruleConditions))
        .count()))
      .thenApply(r -> r.map(loansCount -> loansCount >= itemLimit));
  }

  private boolean isMaterialTypeMatchInRetrievedLoan(
    String expectedMaterialTypeId, Loan loanRecord, AppliedRuleConditions ruleConditions) {

    if (!ruleConditions.isItemTypePresent()) {
      return true;
    }

    return expectedMaterialTypeId != null
      && expectedMaterialTypeId.equals(loanRecord.getItem().getMaterialTypeId());
  }

  private boolean isLoanTypeMatchInRetrievedLoan(
    String expectedLoanType, Loan loanRecord, AppliedRuleConditions ruleConditions) {

    if (!ruleConditions.isLoanTypePresent()) {
      return true;
    }

    return expectedLoanType != null
      && expectedLoanType.equals(loanRecord.getItem().determineLoanTypeForItem());
  }

  private String getErrorMessage(AppliedRuleConditions ruleConditionsEntity) {
    boolean isRuleMaterialTypePresent = ruleConditionsEntity.isItemTypePresent();
    boolean isRuleLoanTypePresent = ruleConditionsEntity.isLoanTypePresent();
    boolean isRulePatronGroupPresent = ruleConditionsEntity.isPatronGroupPresent();

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
}
