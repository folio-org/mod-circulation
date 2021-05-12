package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.resources.renewal.RenewByBarcodeResource;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

public class UnknownLoanPolicyProfileTests {
  @Test
  public void shouldFailCheckOutCalculationForNonRollingProfile() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("Invalid Loan Policy")
      .withLoansProfile("Unknown profile")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final Result<DateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "profile \"Unknown profile\" in the loan policy is not recognised"));
  }

  @Test
  public void shouldFailRenewalCalculationForNonRollingProfile() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("Invalid Loan Policy")
      .withLoansProfile("Unknown profile")
      .create());

    DateTime loanDate = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, DateTime.now(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .anyMatch(httpFailure -> httpFailure.hasErrorWithReason(
        "profile \"Unknown profile\" in the loan policy is not recognised")));
  }

  private Result<Loan> renew(Loan loan, DateTime renewalDate,
    RequestQueue requestQueue, CirculationErrorHandler errorHandler) {

    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(requestQueue);

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, renewalDate)
      .map(RenewalContext::getLoan);
  }
}
