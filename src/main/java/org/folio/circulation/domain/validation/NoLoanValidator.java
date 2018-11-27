package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.HttpResult.of;

import java.util.Optional;
import java.util.function.Supplier;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.HttpResult;

public class NoLoanValidator {
  private final Supplier<HttpFailure> failureSupplier;

  public NoLoanValidator(Supplier<HttpFailure> failureSupplier) {
    this.failureSupplier = failureSupplier;
  }

  public HttpResult<Optional<Loan>> failWhenNoLoan(
    HttpResult<Optional<Loan>> result) {

    return result.failWhen(loan -> of(() -> !loan.isPresent()),
      loans -> failureSupplier.get());
  }
}
