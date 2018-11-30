package org.folio.circulation.domain.validation;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.HttpResult.of;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Optional;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;

import api.support.builders.LoanBuilder;

public class NoLoanValidatorTests {
  @Test
  public void allowSingleLoan() {
    final NoLoanValidator validator = new NoLoanValidator(
      () -> new ServerErrorFailure("No loan"));

    final HttpResult<Optional<Loan>> singleLoan
      = HttpResult.of(() -> Optional.of(generateLoan()));

    final HttpResult<Optional<Loan>> result =
      validator.failWhenNoLoan(singleLoan);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  public void failWhenNoLoans() {
    final NoLoanValidator validator = new NoLoanValidator(
      () -> new ServerErrorFailure("No loan"));

    final HttpResult<Optional<Loan>> result =
      validator.failWhenNoLoan(of(Optional::empty));

    assertThat(result, isErrorFailureContaining("No loan"));
  }

  private Loan generateLoan() {
    return Loan.from(new LoanBuilder().create());
  }
}
