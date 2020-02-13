package org.folio.circulation.domain.validation;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanAndRelatedRecordsValidator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private LoanAndRelatedRecordsValidator() {}

  public static Result<LoanAndRelatedRecords> refuseWhenLoanDueDateUpdateOnClaimedReturned(Result<LoanAndRelatedRecords> result, LoanRepository loanRepository) {
    return result.failWhen(
      r -> {
        Result<Loan> loanResult;
        CompletableFuture<Result<Loan>> future = loanRepository.getById(r.getLoan().getId());

        try {
          loanResult = future.get();

          if (loanResult.succeeded()) {
            return succeeded(isClaimedReturnedOnDueDateChanged(
              r.getLoan(), loanResult.value().getDueDate(), r.getLoan().getDueDate()));
          }
        } catch (ExecutionException | InterruptedException e) {
          log.debug("Failed to fetch existing Loan for reason {} and stacktrace is {}", e.getMessage(), getStackTrace(e));
          return failed(failedDueToServerError(e).cause());
        }

        return succeeded(false);
      },
      LoanAndRelatedRecordsValidator::dueDateChangedFailedForClaimedReturned
    );
  }

  public static Result<LoanAndRelatedRecords> refuseWhenLoanDueDateUpdateOnClaimedReturned(Result<LoanAndRelatedRecords> result, String dueDateParameter) {
    DateTime dueDate = dueDateParameter == null
      || dueDateParameter.isEmpty()
      ? null : DateTime.parse(dueDateParameter);

    return result.failWhen(
      r -> succeeded(isClaimedReturnedOnDueDateChanged(
        r.getLoan(), r.getLoan().getDueDate(), dueDate)),
      LoanAndRelatedRecordsValidator::dueDateChangedFailedForClaimedReturned
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
