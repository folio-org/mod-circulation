package org.folio.circulation.infrastructure.serialization;

import static org.hamcrest.CoreMatchers.is;

import java.io.IOException;
import java.util.UUID;

import org.everit.json.schema.ValidationException;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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

    validator.validate(checkInRequest.encodePrettily());
  }

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void validationFailsWhenRequiredPropertyMissing() throws IOException {
    final JsonSchemaValidator validator = JsonSchemaValidator
      .fromResource("/check-in-by-barcode-request.json");

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
      .atNoServicePoint()
      .create();

    exceptionRule.expect(ValidationException.class);
    exceptionRule.expectMessage(is("#: required key [servicePointId] not found"));

    validator.validate(checkInRequest.encodePrettily());
  }
}
