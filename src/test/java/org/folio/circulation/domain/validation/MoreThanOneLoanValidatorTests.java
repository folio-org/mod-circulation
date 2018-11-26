package org.folio.circulation.domain.validation;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.HttpResult.of;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;

import api.support.builders.LoanBuilder;

public class MoreThanOneLoanValidatorTests {
  @Test
  public void allowSingleLoan() {
    final MoreThanOneLoanValidator validator = new MoreThanOneLoanValidator(
      () -> new ServerErrorFailure("More than one loan"));

    final ArrayList<Loan> loans = new ArrayList<>();
    loans.add(Loan.from(new LoanBuilder().create()));

    final HttpResult<MultipleRecords<Loan>> result =
      validator.failWhenMoreThanOneLoan(
        of(() -> new MultipleRecords<>(loans, loans.size())));

    assertThat(result.succeeded(), is(true));
  }

  @Test
  public void failWhenMoreThanOneLoan() {
    final MoreThanOneLoanValidator validator = new MoreThanOneLoanValidator(
      () -> new ServerErrorFailure("More than one loan"));

    final ArrayList<Loan> loans = new ArrayList<>();
    loans.add(Loan.from(new LoanBuilder().create()));
    loans.add(Loan.from(new LoanBuilder().create()));

    final HttpResult<MultipleRecords<Loan>> result =
      validator.failWhenMoreThanOneLoan(
        of(() -> new MultipleRecords<>(loans, loans.size())));

    assertThat(result, isErrorFailureContaining("More than one loan"));
  }

  @Test
  public void failWhenNoLoans() {
    final MoreThanOneLoanValidator validator = new MoreThanOneLoanValidator(
      () -> new ServerErrorFailure("More than one loan"));

    final ArrayList<Loan> loans = new ArrayList<>();

    final HttpResult<MultipleRecords<Loan>> result =
      validator.failWhenMoreThanOneLoan(
        of(() -> new MultipleRecords<>(loans, loans.size())));

    assertThat(result, isErrorFailureContaining("More than one loan"));
  }
}
