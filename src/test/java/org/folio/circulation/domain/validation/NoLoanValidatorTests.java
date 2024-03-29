package org.folio.circulation.domain.validation;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Optional;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;

class NoLoanValidatorTests {
  @Test
  void allowSingleLoan() {
    final NoLoanValidator validator = new NoLoanValidator(
      () -> new ServerErrorFailure("No loan"));

    final Result<Optional<Loan>> singleLoan
      = of(() -> Optional.of(generateLoan()));

    final Result<Optional<Loan>> result =
      validator.failWhenNoLoan(singleLoan);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  void failWhenNoLoans() {
    final NoLoanValidator validator = new NoLoanValidator(
      () -> new ServerErrorFailure("No loan"));

    final Result<Optional<Loan>> result =
      validator.failWhenNoLoan(of(Optional::empty));

    assertThat(result, isErrorFailureContaining("No loan"));
  }

  private Loan generateLoan() {
    return Loan.from(new LoanBuilder().create());
  }
}
