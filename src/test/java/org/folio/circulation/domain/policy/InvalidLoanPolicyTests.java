package org.folio.circulation.domain.policy;

import static api.support.matchers.FailureMatcher.hasValidationFailure;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;

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

class InvalidLoanPolicyTests {
  @Test
  void shouldFailCheckOutCalculationWhenNoLoanPolicyProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.remove("loansPolicy");

    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final Result<ZonedDateTime> result = loanPolicy.calculateInitialDueDate(loan, null);

    //TODO: This is fairly ugly, replace with a better message
    assertThat(result, hasValidationFailure(
      "profile \"\" in the loan policy is not recognised"));
  }

  @Test
  void shouldFailRenewalWhenNoLoanPolicyProvided() {
    final JsonObject representation = new LoanPolicyBuilder()
      .rolling(Period.from(5, "Unknown"))
      .withName("Invalid Loan Policy")
      .create();

    representation.remove("loansPolicy");

    RenewByBarcodeResource renewByBarcodeResource = new RenewByBarcodeResource(null);
    LoanPolicy loanPolicy = LoanPolicy.from(representation);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 14, 11, 14, 54, 0, UTC);

    Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));
    renewByBarcodeResource.regularRenew(renewalContext, errorHandler, ClockUtil.getZonedDateTime());

    //TODO: This is fairly ugly, replace with a better message
    assertTrue(errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .anyMatch(httpFailure -> httpFailure.hasErrorWithReason("profile \"\" in the loan policy is not recognised")));
  }
}
