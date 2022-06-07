package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.failures.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.failures.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class ChangeDueDateValidator {

  private final ItemStatusValidator itemStatusValidator;

  public ChangeDueDateValidator() {
    this.itemStatusValidator = new ItemStatusValidator(this::dueDateChangeFailedForItem);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseChangeDueDateForItemInDisallowedStatus(
    Result<LoanAndRelatedRecords> loanAndRelatedRecordsResult) {

    return loanAndRelatedRecordsResult.after(relatedRecords -> {
      final Loan changedLoan = relatedRecords.getLoan();

      final Result<LoanAndRelatedRecords> statusValidation = itemStatusValidator
        .refuseWhenItemStatusDoesNotAllowDueDateChange(loanAndRelatedRecordsResult);

      // If the item is not in a status that we're interesting then just skip
      // further logic
      if (statusValidation.succeeded()) {
        return ofAsync(() -> relatedRecords);
      }

      // If the due date is changed, then refuse processing
      // all other changes are allowed
      return completedFuture(loanAndRelatedRecordsResult.map(LoanAndRelatedRecords::getExistingLoan))
        .thenApply(r -> r.failWhen(
          existingLoan -> dueDateHasChanged(existingLoan, changedLoan),
          existingLoan -> statusValidation.cause()))
        .thenApply(r -> r.map(notUsed -> relatedRecords));
    });
  }

  private Result<Boolean> dueDateHasChanged(Loan existingLoan, Loan changedLoan) {
    return succeeded(existingLoan != null
        && !isSameMillis(existingLoan.getDueDate(), changedLoan.getDueDate()));
  }

  private ValidationErrorFailure dueDateChangeFailedForItem(Item item) {
    return singleValidationError("item is " + item.getStatusName(),
      "itemId", item.getItemId());
  }
}
