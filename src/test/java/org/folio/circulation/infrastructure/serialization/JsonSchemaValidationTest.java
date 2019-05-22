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
    final Schema schema = getSchema("/check-in-by-barcode-request.json");
    final JsonSchemaValidator validator = new JsonSchemaValidator(schema);

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
    final Schema schema = getSchema("/check-in-by-barcode-request.json");
    final JsonSchemaValidator validator = new JsonSchemaValidator(schema);

    final JsonObject checkInRequest = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("246650492")
      .on(DateTime.now())
      .atNoServicePoint()
      .create();

    exceptionRule.expect(ValidationException.class);
    exceptionRule.expectMessage(is("#: required key [servicePointId] not found"));

    validator.validate(checkInRequest.encodePrettily());
  }

  private Schema getSchema(String path) throws IOException {
    try (InputStream inputStream = getClass()
        .getResourceAsStream(path)) {

      JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));

      return SchemaLoader.load(rawSchema);
    }
  }
}
