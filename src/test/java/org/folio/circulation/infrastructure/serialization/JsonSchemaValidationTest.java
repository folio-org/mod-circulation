package org.folio.circulation.infrastructure.serialization;

import static org.hamcrest.CoreMatchers.is;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;

public class JsonSchemaValidationTest {
  @Test
  public void validationSucceedWithCompleteExample() throws IOException {
    Schema schema = getCheckInByBarcodeSchema();

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
      .at(UUID.randomUUID())
      .create();

    final JSONObject example = new JSONObject(checkInRequest.encodePrettily());

    schema.validate(example);
  }

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void validationFailsWhenRequiredPropertyMissing() throws IOException {
    Schema schema = getCheckInByBarcodeSchema();

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
      .atNoServicePoint()
      .create();

    final JSONObject example = new JSONObject(checkInRequest.encodePrettily());

    exceptionRule.expect(ValidationException.class);
    exceptionRule.expectMessage(is("#: required key [servicePointId] not found"));

    schema.validate(example);
  }

  private Schema getCheckInByBarcodeSchema() throws IOException {
    try (InputStream inputStream = getClass()
        .getResourceAsStream("/check-in-by-barcode-request.json")) {

      JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));

      return SchemaLoader.load(rawSchema);
    }
  }
}
