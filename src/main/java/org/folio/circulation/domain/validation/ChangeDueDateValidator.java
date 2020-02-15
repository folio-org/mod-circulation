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

public class ChangeDueDateValidator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final LoanRepository loanRepository;

  public ChangeDueDateValidator(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  public Result<LoanAndRelatedRecords> refuseDueDateChangeWhenClaimedReturned(
    Result<LoanAndRelatedRecords> result) {

    return result.failWhen(
      r -> {
        Result<Loan> loanResult;
        CompletableFuture<Result<Loan>> future = getExistingLoan(r);

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
      ChangeDueDateValidator::dueDateChangedFailedForClaimedReturned
    );
  }

  private CompletableFuture<Result<Loan>> getExistingLoan(
      LoanAndRelatedRecords loanAndRelatedRecords) {

    return loanRepository.getById(loanAndRelatedRecords.getLoan().getId());
  }

  private static boolean isClaimedReturnedOnDueDateChanged(Loan loan,
      DateTime previous, DateTime upcoming) {

    if (!loan.getItem().isClaimedReturned()) {
      return false;
    }

    return !previous.equals(upcoming);
  }

  private static ValidationErrorFailure dueDateChangedFailedForClaimedReturned(
      LoanAndRelatedRecords record) {

    return singleValidationError("item is claimed returned", "id",
      record.getLoan().getId());
  }
}
