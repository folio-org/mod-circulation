package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ChangeDueDateValidator {
  private final LoanRepository loanRepository;

  public ChangeDueDateValidator(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseChangeDueDateForItemInStatus(
    Result<LoanAndRelatedRecords> loanAndRelatedRecordsResult) {

    return loanAndRelatedRecordsResult.after(context -> refuseWhenDueDateChanged(context,
      Loan::isDeclaredLost,
      Loan::isClaimedReturned,
      Loan::isAgedToLost));
  }

  @SafeVarargs
  private final CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenDueDateChanged(
    LoanAndRelatedRecords relatedRecords, Predicate<Loan>... refusePredicates) {

    final Loan changedLoan = relatedRecords.getLoan();
    final boolean caseDoesNotMatch = Arrays.stream(refusePredicates)
      .noneMatch(predicate -> predicate.test(changedLoan));

    if (caseDoesNotMatch) {
      return CompletableFuture.completedFuture(succeeded(relatedRecords));
    }

    return getExistingLoan(changedLoan)
      .thenApply(r -> r.failWhen(
        existingLoan -> dueDateHasChanged(existingLoan, changedLoan),
        existingLoan -> dueDateChangedFailedForClaimedReturned(changedLoan)))
      .thenApply(r -> r.map(notUsed -> relatedRecords));
  }

  private Result<Boolean> dueDateHasChanged(Loan existingLoan, Loan changedLoan) {
    return Result.of(() ->
        existingLoan != null
            && !existingLoan.getDueDate().equals(changedLoan.getDueDate()));
  }

  private CompletableFuture<Result<Loan>> getExistingLoan(Loan loan) {
    return loanRepository.getById(loan.getId());
  }

  private ValidationErrorFailure dueDateChangedFailedForClaimedReturned(Loan loan) {
    return singleValidationError("item is " + loan.getItem().getStatusName(),
      "itemId", loan.getItemId());
  }
}
