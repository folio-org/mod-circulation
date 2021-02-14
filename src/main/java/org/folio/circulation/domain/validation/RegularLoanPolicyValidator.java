package org.folio.circulation.domain.validation;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_IS_NOT_LOANABLE;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.validation.overriding.Validator;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class RegularLoanPolicyValidator implements Validator<LoanAndRelatedRecords> {
  private final Function<LoanPolicy, ValidationErrorFailure> itemLimitErrorFunction;

  public RegularLoanPolicyValidator(Function<LoanPolicy, ValidationErrorFailure> itemLimitErrorFunction) {
    this.itemLimitErrorFunction = itemLimitErrorFunction;
  }

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(
    LoanAndRelatedRecords records) {

    return refuseWhenItemIsNotLoanable(records);
  }

  @Override
  public CirculationErrorType getErrorType() {
    return ITEM_IS_NOT_LOANABLE;
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemIsNotLoanable(
    LoanAndRelatedRecords relatedRecords) {

    return ofAsync(relatedRecords.getLoan()::getLoanPolicy)
      .thenComposeAsync(result -> result.failAfter(this::isNotLoanablePolicy,
        itemLimitErrorFunction::apply))
      .thenApply(result -> result.map(v -> relatedRecords));
  }

  private CompletableFuture<Result<Boolean>> isNotLoanablePolicy(LoanPolicy loanPolicy) {
    return ofAsync(loanPolicy::isNotLoanable);
  }
}
