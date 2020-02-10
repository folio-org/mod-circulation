package org.folio.circulation.domain.validation;

import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static org.folio.circulation.domain.validation.InactiveUserValidator.forUser;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class InactiveUserValidatorTests {
  @Test
  public void allowActiveUser() {
    final User steve = new User(basedUponStevenJones().create());

    final InactiveUserValidator validator = forUser(steve.getBarcode());

    final Result<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(steve, null);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  public void refuseInactiveUser() {
    final User steve = new User(basedUponStevenJones()
      .inactive()
      .create());

    final InactiveUserValidator validator = forUser(steve.getBarcode());

    final Result<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(steve, null);

    assertThat(result.failed(), is(true));
  }

  @Test
  public void refuseWhenUserIsNeitherActiveNorInactive() {
    final User steve = new User(basedUponStevenJones()
      .neitherActiveOrInactive()
      .create());

    final InactiveUserValidator validator = forUser(steve.getBarcode());

    final Result<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(steve, null);

    assertThat(result.failed(), is(true));
  }

  @Test
  public void allowNullUser() {
    final InactiveUserValidator validator = forUser("");

    final Result<LoanAndRelatedRecords> result =
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
      "", "", s -> singleValidationError("failed", "", ""));

    final Result<LoanAndRelatedRecords> result =
      validator.refuseWhenUserIsInactive(succeeded(new LoanAndRelatedRecords(
        Loan.from(new JsonObject()))));

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
  }
}
