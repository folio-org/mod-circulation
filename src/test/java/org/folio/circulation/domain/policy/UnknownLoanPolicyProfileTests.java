package org.folio.circulation.domain.policy;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static api.support.matchers.FailureMatcher.isValidationFailure;
import static org.junit.Assert.assertThat;

public class UnknownLoanPolicyProfileTests {
  @Test
  public void shouldFailForNonRollingProfile() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("Invalid Loan Policy")
      .withLoansProfile("Unknown profile")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final HttpResult<DateTime> result = loanPolicy.calculate(loan);

    assertThat(result, isValidationFailure(
      "Item can't be checked out as profile \"Unknown profile\" in the loan policy is not recognised. " +
        "Please review \"Invalid Loan Policy\" before retrying checking out"));
  }
}
