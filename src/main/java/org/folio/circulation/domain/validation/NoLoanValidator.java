package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.of;

import java.util.Optional;
import java.util.function.Supplier;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.failures.HttpFailure;
import org.folio.circulation.support.results.Result;

public class NoLoanValidator {
  private final Supplier<HttpFailure> failureSupplier;

  public NoLoanValidator(Supplier<HttpFailure> failureSupplier) {
    this.failureSupplier = failureSupplier;
  }

  public Result<Optional<Loan>> failWhenNoLoan(
    Result<Optional<Loan>> result) {

    return result.failWhen(loan -> of(() -> loan.isEmpty()),
      loans -> failureSupplier.get());
  }
}
