package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.LogUtil.resultAsString;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class ChangeDueDateValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ItemStatusValidator itemStatusValidator;

  public ChangeDueDateValidator() {
    this.itemStatusValidator = new ItemStatusValidator(this::dueDateChangeFailedForItem);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseChangeDueDateForItemInDisallowedStatus(
    Result<LoanAndRelatedRecords> loanAndRelatedRecordsResult) {

    log.debug("refuseChangeDueDateForItemInDisallowedStatus:: parameters " +
        "loanAndRelatedRecordsResult: {}", () -> resultAsString(loanAndRelatedRecordsResult));

    return loanAndRelatedRecordsResult.after(relatedRecords -> {
      log.debug("refuseChangeDueDateForItemInDisallowedStatus:: after relatedRecords: {}",
        relatedRecords);

      final Loan changedLoan = relatedRecords.getLoan();

      final Result<LoanAndRelatedRecords> statusValidation = itemStatusValidator
        .refuseWhenItemStatusDoesNotAllowDueDateChange(loanAndRelatedRecordsResult);

      // If the item is not in a status that we're interesting then just skip
      // further logic
      if (statusValidation.succeeded()) {
        log.debug("refuseChangeDueDateForItemInDisallowedStatus:: statusValidation succeeded");
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
    log.debug("dueDateHasChanged:: parameters existingLoan: {}, changedLoan: {}", existingLoan,
      changedLoan);
    boolean result = existingLoan != null
      && !isSameMillis(existingLoan.getDueDate(), changedLoan.getDueDate());
    log.info("dueDateHasChanged:: result {}", result);
    return succeeded(result);
  }

  private ValidationErrorFailure dueDateChangeFailedForItem(Item item) {
    log.debug("dueDateChangeFailedForItem:: parameters item: {}", item);
    return singleValidationError("item is " + item.getStatusName(),
      "itemId", item.getItemId());
  }
}
