package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemLimitValidator {
  private final Function<String, ValidationErrorFailure> itemLimitErrorFunction;
  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public ItemLimitValidator(Function<String, ValidationErrorFailure> itemLimitErrorFunction,
    LoanRepository loanRepository, LoanPolicyRepository loanPolicyRepository) {

    this.itemLimitErrorFunction = itemLimitErrorFunction;
    this.loanRepository = loanRepository;
    this.loanPolicyRepository = loanPolicyRepository;
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
          return itemLimitErrorFunction.apply(String.format("Patron has reached maximum item limit of %d items %s",
            itemLimit, message));
        }))
      .thenApply(result -> result.map(v -> records));
  }

  private CompletableFuture<Result<Boolean>> isLimitReached(List<String> ruleConditions,
                                                            LoanAndRelatedRecords records) {

    if (!isRuleMaterialTypePresent(ruleConditions) && !isRuleLoanTypePresent(ruleConditions)) {
      return ofAsync(() -> false);
    }

    Item item = records.getLoan().getItem();
    String materialTypeId = item.getMaterialType() != null
      ? item.getMaterialType().getString("id")
      : null;
    String loanType = item.getLoanTypeName();
    Integer itemLimit = records.getLoan().getLoanPolicy().getItemLimit();

    return loanRepository.findOpenLoansByUserIdWithItem(records)
      .thenApply(r -> r.map(loans -> loans.getRecords().stream()
        .filter(loan -> isMaterialTypeMatchInRetrievedLoan(materialTypeId, loan))
        .filter(loan -> isLoanTypeMatchInRetrievedLoan(loanType, loan))
        .count()))
      .thenApply(r -> r.map(loansCount -> loansCount >= itemLimit));
  }

  private boolean isMaterialTypeMatchInRetrievedLoan(String expectedMaterialTypeId, Loan loan) {
    return expectedMaterialTypeId != null
      && expectedMaterialTypeId.equals(loan.getItem().getMaterialType().getString("id"));
  }

  private boolean isLoanTypeMatchInRetrievedLoan(String expectedLoanType, Loan loan) {
    return expectedLoanType != null
      && expectedLoanType.equalsIgnoreCase(loan.getItem().getLoanTypeName());
  }

  private boolean isRuleMaterialTypePresent(List<String> conditions) {
    return conditions.contains("ItemType");
  }

  private boolean isRuleLoanTypePresent(List<String> conditions) {
    return conditions.contains("LoanType");
  }

  private boolean isRulePatronGroupPresent(List<String> conditions) {
    return conditions.contains("PatronGroup");
  }

  private String getErrorMessage(List<String> conditions) {
    boolean isRuleMaterialTypePresent = isRuleMaterialTypePresent(conditions);
    boolean isRuleLoanTypePresent = isRuleLoanTypePresent(conditions);
    boolean isRulePatronGroupPresent = isRulePatronGroupPresent(conditions);

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
