package org.folio.circulation.infrastructure.serialization;

import static api.support.fakes.StorageSchema.validatorForStorageLoanSchema;
import static api.support.matchers.ResultMatchers.succeeded;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.isErrorWith;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.UUID;

import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

public class JsonSchemaValidationTest {
  @Test
  public void validationSucceedWithCompleteExample() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
      .at(UUID.randomUUID())
      .create();

    assertThat(validator.validate(checkInRequest.encode()), succeeded());
  }

  @Test
  public void canValidateSchemaWithReferences() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/request.json");

    final JsonObject request = new RequestBuilder()
      .hold()
      .withItemId(UUID.randomUUID())
      .withRequesterId(UUID.randomUUID())
      .fulfilToHoldShelf(UUID.randomUUID())
      .withRequestDate(DateTime.now())
      .create();

    assertThat(validator.validate(request.encode()), succeeded());
  }

  @Test
  public void canValidateStorageSchema() throws IOException {
    final JsonSchemaValidator validator = validatorForStorageLoanSchema();

    final JsonObject storageLoanRequest = new JsonObject();

    write(storageLoanRequest, "itemId", UUID.randomUUID());
    write(storageLoanRequest, "userId", UUID.randomUUID());
    write(storageLoanRequest, "loanDate", DateTime.now());
    write(storageLoanRequest, "action", "checkedout");

    assertThat(validator.validate(storageLoanRequest.encode()), succeeded());
  }

  @Test
  public void validationFailsWhenUnexpectedPropertyIncludedInStorageSchema() throws IOException {
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
  public void validationFailsWhenRequiredPropertyMissing() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
      .atNoServicePoint()
      .create();

    final Result<String> result = validator.validate(checkInRequest.encode());

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), isErrorWith(allOf(
      hasMessage("#: required key [servicePointId] not found"),
      hasParameter(null, null))));
  }

  @Test
  public void validationFailsWhenAnUnexpectedPropertyIsPresent() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
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
  public void validationFailsForMultipleReasons() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
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
  public void validationFailsForInvalidJson() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final Result<String> result = validator.validate("foo blah");

    assertThat(result.succeeded(), is(false));

    assertThat(result.cause(), instanceOf(BadRequestFailure.class));

    final BadRequestFailure badRequestFailure = (BadRequestFailure)result.cause();

    assertThat(badRequestFailure.getReason(), is("Cannot parse \"foo blah\" as JSON"));
  }
}
