package org.folio.circulation.domain.validation;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;

class MoreThanOneLoanValidatorTests {
  @Test
  void allowSingleLoan() {
    final MoreThanOneLoanValidator validator = new MoreThanOneLoanValidator(
      () -> new ServerErrorFailure("More than one loan"));

    final Result<MultipleRecords<Loan>> multipleLoans
      = multipleLoansResult(generateLoan());

    final Result<MultipleRecords<Loan>> result =
      validator.failWhenMoreThanOneLoan(multipleLoans);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  void failWhenMoreThanOneLoan() {
    final MoreThanOneLoanValidator validator = new MoreThanOneLoanValidator(
      () -> new ServerErrorFailure("More than one loan"));

    final Result<MultipleRecords<Loan>> multipleLoans
      = multipleLoansResult(generateLoan(), generateLoan());

    final Result<MultipleRecords<Loan>> result =
      validator.failWhenMoreThanOneLoan(
        multipleLoans);

    assertThat(result, isErrorFailureContaining("More than one loan"));
  }

  @Test
  void allowWhenNoLoans() {
    final MoreThanOneLoanValidator validator = new MoreThanOneLoanValidator(
      () -> new ServerErrorFailure("More than one loan"));

    final Result<MultipleRecords<Loan>> noLoans
      = multipleLoansResult();

    final Result<MultipleRecords<Loan>> result =
      validator.failWhenMoreThanOneLoan(noLoans);

    assertThat(result.succeeded(), is(true));
  }

  private Loan generateLoan() {
    return Loan.from(new LoanBuilder().create());
  }

  private Result<MultipleRecords<Loan>> multipleLoansResult(Loan... loans) {
    final List<Loan> loansList = Arrays.asList(loans);

    return of(() -> new MultipleRecords<>(loansList, loansList.size()));
  }
}
