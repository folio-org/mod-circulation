package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.failures.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.failures.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;
import lombok.val;

class ItemStatusValidatorTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "Long missing",
    "In process (non-requestable)",
    "Restricted",
    "Unavailable",
    "Unknown"
  })
  void canCheckOutItemInAllowedStatus(String itemStatus) {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemIsNotAllowedForCheckOut(loanWithItemInStatus(itemStatus));

    assertTrue(validationResult.succeeded());
    assertThat(validationResult.value(), notNullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Declared lost",
    "Claimed returned",
    "Aged to lost",
    "Intellectual item"
  })
  void cannotCheckOutItemInDisallowedStatus(String itemStatus) {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemIsNotAllowedForCheckOut(loanWithItemInStatus(itemStatus));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Declared lost",
    "Claimed returned",
    "Aged to lost"
  })
  void cannotChangeDueDateForItemInDisallowedStatus(String itemStatus) {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemStatusDoesNotAllowDueDateChange(loanWithItemInStatus(itemStatus));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  private Result<LoanAndRelatedRecords> loanWithItemInStatus(String itemStatus) {
    val itemRepresentation = new JsonObject()
      .put("status", new JsonObject().put("name", itemStatus));
    val item = Item.from(itemRepresentation);

    val loan = Loan.from(new JsonObject()).withItem(item);
    val context = new LoanAndRelatedRecords(loan);

    return succeeded(context.withItem(item));
  }

  private ValidationErrorFailure validationError(Item item) {
    return singleValidationError("error", "barcode", "some-barcode");
  }
}
