package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.of;

import java.util.function.Function;
import java.util.function.Supplier;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;

public class MoreThanOneLoanValidator {
  private final Supplier<HttpFailure> failureSupplier;

  public MoreThanOneLoanValidator(Supplier<HttpFailure> failureSupplier) {
    this.failureSupplier = failureSupplier;
  }

  public Result<MultipleRecords<Loan>> failWhenMoreThanOneLoan(
    Result<MultipleRecords<Loan>> result) {

    return result.failWhen(moreThanOneLoan(),
      loans -> failureSupplier.get());
  }

  private static Function<MultipleRecords<Loan>, Result<Boolean>> moreThanOneLoan() {
    return loans -> of(() -> loans.getTotalRecords() > 1);
  }
}
