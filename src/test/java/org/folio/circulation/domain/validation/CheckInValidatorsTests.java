package org.folio.circulation.domain.validation;

import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.isErrorWith;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.ValidationErrorFailure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;
import lombok.val;

class CheckInValidatorsTests {
  @ParameterizedTest
  @ValueSource(strings = {
    "Available",
    "Long missing",
    "In process (non-requestable)",
    "Restricted",
    "Unavailable",
    "Unknown"
  })
  void canCheckInItemInAllowedStatus(String itemStatus) {
    final var validator = new CheckInValidators(this::validationError);

    final var item = itemIn(itemStatus);

    final var validationResult = validator
      .refuseWhenItemIsNotAllowedForCheckIn(item);

    assertTrue(validationResult.succeeded());
    assertThat(validationResult.value(), is(item));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Intellectual item"
  })
  void cannotCheckInItemInDisallowedStatus(String itemStatus) {
    val validator = new CheckInValidators(this::validationError);

    val validationResult = validator
      .refuseWhenItemIsNotAllowedForCheckIn(itemIn(itemStatus));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), isErrorWith(allOf(
      hasMessage("error"),
      hasParameter("barcode", "some-barcode"))));
  }

  private Item itemIn(String itemStatus) {
    val itemRepresentation = new JsonObject()
      .put("status", new JsonObject().put("name", itemStatus));

    return Item.from(itemRepresentation);
  }

  private ValidationErrorFailure validationError(Item item) {
    return singleValidationError("error", "barcode", "some-barcode");
  }
}
