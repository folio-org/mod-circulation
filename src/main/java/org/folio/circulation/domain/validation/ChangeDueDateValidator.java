package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ChangeDueDateValidator {
  private final LoanRepository loanRepository;

  public ChangeDueDateValidator(LoanRepository loanRepository) {
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenClaimedReturned(
      Result<LoanAndRelatedRecords> changedLoanResult) {

    return changedLoanResult
      .map(LoanAndRelatedRecords::getLoan)
      .afterWhen(this::itemIsClaimedReturned,
        this::refuseWhenDueDateChanged, this::carryOn)
      .thenApply(r -> r.next(existingLoan -> changedLoanResult));
  }

  private CompletableFuture<Result<Boolean>> itemIsClaimedReturned(
      Loan changedLoan) {

    return Result.ofAsync(() -> changedLoan.getItem().isClaimedReturned());
  }

  private CompletableFuture<Result<Loan>> refuseWhenDueDateChanged(
      Loan changedLoan) {

    return getExistingLoan(changedLoan)
      .thenApply(r -> r.failWhen(
        existingLoan -> dueDateHasChanged(existingLoan, changedLoan),
        existingLoan -> dueDateChangedFailedForClaimedReturned(changedLoan)));
  }

  private CompletableFuture<Result<Loan>> carryOn(Loan changedLoan) {
    return Result.ofAsync(() -> changedLoan);
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
    return singleValidationError("item is claimed returned", "id", loan.getId());
  }
}
