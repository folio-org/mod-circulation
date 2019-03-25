package org.folio.circulation.domain.validation;

import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static org.folio.circulation.domain.validation.InactiveUserValidator.forUser;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failure;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class InactiveUserValidatorTests {
  @Test
  public void allowActiveUser() {
    final User steve = new User(basedUponStevenJones().create());

    final InactiveUserValidator validator = forUser(steve.getBarcode());

    final HttpResult<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(steve, null);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  public void refuseInactiveUser() {
    final User steve = new User(basedUponStevenJones()
      .inactive()
      .create());

    final InactiveUserValidator validator = forUser(steve.getBarcode());

    final HttpResult<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(steve, null);

    assertThat(result.failed(), is(true));
  }

  @Test
  public void refuseWhenUserIsNeitherActiveNorInactive() {
    final User steve = new User(basedUponStevenJones()
      .neitherActiveOrInactive()
      .create());

    final InactiveUserValidator validator = forUser(steve.getBarcode());

    final HttpResult<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(steve, null);

    assertThat(result.failed(), is(true));
  }

  @Test
  public void allowNullUser() {
    final InactiveUserValidator validator = forUser("");

    final HttpResult<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(null, null);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  public void failsWhenExceptionIsThrown() {
    final User steve = new User(basedUponStevenJones()
      .neitherActiveOrInactive()
      .create());

    final InactiveUserValidator validator = new InactiveUserValidator(
      records -> { throw new RuntimeException("Something went wrong"); },
      "", "", s -> failure("failed", "", ""));

    final HttpResult<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(succeeded(new LoanAndRelatedRecords(
        new Loan(new JsonObject(), null, steve, null, null, null))));

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
  }
}
