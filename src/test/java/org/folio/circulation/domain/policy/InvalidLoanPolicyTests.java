package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static org.junit.Assert.assertThat;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

import java.util.Collections;

public class InvalidLoanPolicyTests {
  @Test
  public void shouldFailCheckOutCalculationWhenNoLoanPolicyProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.remove("loansPolicy");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan);

    //TODO: This is fairly ugly, replace with a better message
    assertThat(result, hasValidationFailure(
      "profile \"\" in the loan policy is not recognised"));
  }

  @Test
  public void shouldFailRenewalWhenNoLoanPolicyProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.remove("loansPolicy");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final Result<Loan> result = loanPolicy.renew(loan, DateTime.now(), new RequestQueue(Collections.emptyList()));

    //TODO: This is fairly ugly, replace with a better message
    assertThat(result, hasValidationFailure(
      "profile \"\" in the loan policy is not recognised"));
  }
}
