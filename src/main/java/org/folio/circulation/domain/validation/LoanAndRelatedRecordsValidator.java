package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

public class LoanAndRelatedRecordsValidator {

  private LoanAndRelatedRecordsValidator() {}

  public static Result<LoanAndRelatedRecords> refuseWhenLoanDueDateUpdateOnClaimedReturned(Result<LoanAndRelatedRecords> result, Result<Loan> previous) {
    return result.failWhen(
      r -> previous.next(loan -> {
        return succeeded(isClaimedReturnedOnDueDateChanged(
          r.getLoan(), loan.getDueDate(), r.getLoan().getDueDate()));
      }),
      r -> dueDateChangedFailedForClaimedReturned(r)
    );
  }

  public static Result<LoanAndRelatedRecords> refuseWhenLoanDueDateUpdateOnClaimedReturned(Result<LoanAndRelatedRecords> result, String dueDateParameter) {
    DateTime dueDate = dueDateParameter == null
      || dueDateParameter.isEmpty()
      ? null : DateTime.parse(dueDateParameter);

    return result.failWhen(
      r -> succeeded(isClaimedReturnedOnDueDateChanged(
        r.getLoan(), r.getLoan().getDueDate(), dueDate)),
      r -> dueDateChangedFailedForClaimedReturned(r)
    );
  }

  private static boolean isClaimedReturnedOnDueDateChanged(Loan loan, DateTime previous, DateTime upcoming) {
    if (!loan.getItem().isClaimedReturned() || upcoming == null) {
      return false;
    }

    if (previous == null) {
      return true;
    }

    return !previous.toString().equals(upcoming.toString());
  }

  private static ValidationErrorFailure dueDateChangedFailedForClaimedReturned(LoanAndRelatedRecords record) {
    return singleValidationError(
      "Due date change failed: item is claimed returned", "id",
      record.getLoan().getId());
  }
}
