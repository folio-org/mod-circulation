package org.folio.circulation.infrastructure.serialization;

import static api.support.fakes.StorageSchema.validatorForStorageLoanSchema;
import static api.support.matchers.ResultMatchers.succeeded;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.isErrorWith;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.UUID;

import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

class JsonSchemaValidationTest {
  @Test
  void validationSucceedWithCompleteExample() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(ClockUtil.getZonedDateTime())
      .at(UUID.randomUUID())
      .create();

    assertThat(validator.validate(checkInRequest.encode()), succeeded());
  }

  @Test
  void canValidateSchemaWithReferences() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/request.json");

    final JsonObject request = new RequestBuilder()
      .hold()
      .itemRequestLevel()
      .withItemId(UUID.randomUUID())
      .withInstanceId(UUID.randomUUID())
      .withRequesterId(UUID.randomUUID())
      .fulfillToHoldShelf(UUID.randomUUID())
      .withRequestDate(ClockUtil.getZonedDateTime())
      .create();

    assertThat(validator.validate(request.encode()), succeeded());
  }

  @Test
  void canValidateStorageSchema() throws IOException {
    final JsonSchemaValidator validator = validatorForStorageLoanSchema();

    final JsonObject storageLoanRequest = new JsonObject();

    write(storageLoanRequest, "itemId", UUID.randomUUID());
    write(storageLoanRequest, "userId", UUID.randomUUID());
    write(storageLoanRequest, "loanDate", ClockUtil.getZonedDateTime());
    write(storageLoanRequest, "action", "checkedout");

    assertThat(validator.validate(storageLoanRequest.encode()), succeeded());
  }

  @Test
  void validationFailsWhenUnexpectedPropertyIncludedInStorageSchema() throws IOException {
    final JsonSchemaValidator validator = validatorForStorageLoanSchema();

    final JsonObject storageLoanRequest = new JsonObject()
      .put("unexpectedProperty", "foo");

    final Result<String> result = validator.validate(storageLoanRequest.encode());

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), isErrorWith(allOf(
      hasMessage("#: extraneous key [unexpectedProperty] is not permitted"),
      hasParameter(null, null))));
  }

  @Test
  void validationFailsWhenRequiredPropertyMissing() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(ClockUtil.getZonedDateTime())
      .atNoServicePoint()
      .create();

    final Result<String> result = validator.validate(checkInRequest.encode());

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), isErrorWith(allOf(
      hasMessage("#: required key [servicePointId] not found"),
      hasParameter(null, null))));
  }

  @Test
  void validationFailsWhenAnUnexpectedPropertyIsPresent() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(ClockUtil.getZonedDateTime())
      .at(UUID.randomUUID())
      .create();

    checkInRequest.put("unexpectedProperty", "foo");

    final Result<String> result = validator.validate(checkInRequest.encode());

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), isErrorWith(allOf(
      hasMessage("#: extraneous key [unexpectedProperty] is not permitted"),
      hasParameter(null, null))));
  }

  @Test
  void validationFailsForMultipleReasons() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(ClockUtil.getZonedDateTime())
      .atNoServicePoint()
      .create();

    checkInRequest.put("unexpectedProperty", "foo");

    final Result<String> result = validator.validate(checkInRequest.encode());

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), isErrorWith(allOf(
      hasMessage("#: extraneous key [unexpectedProperty] is not permitted"),
      hasParameter(null, null))));

    assertThat(result.cause(), isErrorWith(allOf(
      hasMessage("#: required key [servicePointId] not found"),
      hasParameter(null, null))));
  }

  @Test
  void validationFailsForInvalidJson() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final Result<String> result = validator.validate("foo blah");

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), instanceOf(BadRequestFailure.class));

    final BadRequestFailure badRequestFailure = (BadRequestFailure)result.cause();

    assertThat(badRequestFailure.getReason(), is("Cannot parse \"foo blah\" as JSON"));
  }
}
