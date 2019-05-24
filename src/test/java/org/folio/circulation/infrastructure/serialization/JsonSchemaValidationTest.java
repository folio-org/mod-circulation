package org.folio.circulation.infrastructure.serialization;

import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.isErrorWith;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.UUID;

import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
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

    assertThat(validator.validate(checkInRequest.encode()).succeeded(), is(true));
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
}
