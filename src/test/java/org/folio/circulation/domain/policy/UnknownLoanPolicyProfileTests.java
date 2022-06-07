package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.Collections;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.resources.renewal.RenewByBarcodeResource;
import org.folio.circulation.support.failures.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

class UnknownLoanPolicyProfileTests {
  @Test
  void shouldFailCheckOutCalculationForNonRollingProfile() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("Invalid Loan Policy")
      .withLoansProfile("Unknown profile")
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    assertThat(result, hasValidationFailure(
      "profile \"Unknown profile\" in the loan policy is not recognised"));
  }

  @Test
  void shouldFailRenewalCalculationForNonRollingProfile() {
    LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withName("Invalid Loan Policy")
      .withLoansProfile("Unknown profile")
      .create());

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, ClockUtil.getZonedDateTime(), new RequestQueue(Collections.emptyList()), errorHandler);

    assertTrue(errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .anyMatch(httpFailure -> httpFailure.hasErrorWithReason(
        "profile \"Unknown profile\" in the loan policy is not recognised")));
  }

  private Result<Loan> renew(Loan loan, ZonedDateTime renewalDate,
    RequestQueue requestQueue, CirculationErrorHandler errorHandler) {

    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(requestQueue);

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, renewalDate)
      .map(RenewalContext::getLoan);
  }
}
