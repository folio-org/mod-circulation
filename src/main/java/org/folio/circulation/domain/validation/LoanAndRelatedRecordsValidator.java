package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

public class LoanAndRelatedRecordsValidator {

  private LoanAndRelatedRecordsValidator() {}

  public static Result<LoanAndRelatedRecords> refuseWhenLoanDueDateUpdateOnClaimedReturned(Result<LoanAndRelatedRecords> result, LoanRepository loanRepository) {
    return result.failWhen(
      r -> {
        Result<Loan> loanResult;

        try {
          loanResult = loanRepository.getById(r.getLoan().getId()).get();

          if (loanResult.succeeded()) {
            return succeeded(isClaimedReturnedOnDueDateChanged(
              r.getLoan(), loanResult.value().getDueDate(), r.getLoan().getDueDate()));
          }
        } catch (InterruptedException | ExecutionException e) {
          e.printStackTrace();
        }

        return succeeded(false);
      },
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
    if (!loan.getItem().isClaimedReturned() || upcoming == null
      || previous == null) {
      return false;
    }

    return !previous.toString().equals(upcoming.toString());
  }

  private static ValidationErrorFailure dueDateChangedFailedForClaimedReturned(LoanAndRelatedRecords record) {
    return singleValidationError(
      "Due date change failed: item is claimed returned", "id",
      record.getLoan().getId());
  }
}
