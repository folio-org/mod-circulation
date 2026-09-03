package org.folio.circulation.domain.validation;

import static api.support.matchers.ResultMatchers.hasValidationError;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;
import io.vertx.core.json.JsonObject;

class InactiveUserRenewalValidatorTest {
  private final InactiveUserRenewalValidator validator = new InactiveUserRenewalValidator();

  @Test
  void refuseWhenUserIsInactive() {
    final Result<RenewalContext> result = validator.refuseWhenPatronIsInactive(
      contextFor(new JsonObject().put("active", false))).join();

    assertThat(result, hasValidationError(allOf(
      hasMessage("Cannot renew loan when user is inactive or expired"),
      hasParameter("reason", "User is inactive."),
      hasCode(ErrorCode.USER_IS_INACTIVE_OR_EXPIRED))));
  }

  @Test
  void refuseWhenUserStatusCannotBeDetermined() {
    final Result<RenewalContext> result = validator.refuseWhenPatronIsInactive(
      contextFor(new JsonObject())).join();

    assertThat(result, hasValidationError(allOf(
      hasMessage("Cannot determine if user is active."),
      hasParameter("reason", "Cannot determine if user active."),
      hasCode(ErrorCode.USER_STATUS_CANNOT_BE_DETERMINED))));
  }

  private RenewalContext contextFor(JsonObject userRepresentation) {
    userRepresentation.put("id", UUID.randomUUID().toString());

    final Loan loan = new LoanBuilder().asDomainObject()
      .withUser(new User(userRepresentation));

    return RenewalContext.create(loan, new JsonObject(), "no-user");
  }
}
