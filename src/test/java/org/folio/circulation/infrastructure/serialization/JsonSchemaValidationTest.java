package org.folio.circulation.infrastructure.serialization;

import static org.hamcrest.CoreMatchers.is;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JsonSchemaValidationTest {
  @Test
  public void validationSucceedWithCompleteExample() throws IOException {
    try (InputStream inputStream = getClass().getResourceAsStream(
      "/check-in-by-barcode-request.json")) {

      JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
      Schema schema = SchemaLoader.load(rawSchema);

      final JSONObject example = new JSONObject();

      example.put("itemBarcode", "246650492");
      example.put("servicePointId", UUID.randomUUID().toString());
      example.put("checkInDate", DateTime.now().toString(ISODateTimeFormat.dateTime()));

      schema.validate(example);
    }
  }

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void validationFailsWhenRequiredPropertyMissing() throws IOException {
    try (InputStream inputStream = getClass().getResourceAsStream(
      "/check-in-by-barcode-request.json")) {

      JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
      Schema schema = SchemaLoader.load(rawSchema);

      final JSONObject example = new JSONObject();

      example.put("itemBarcode", "246650492");
      example.put("checkInDate", DateTime.now().toString(ISODateTimeFormat.dateTime()));

      exceptionRule.expect(ValidationException.class);
      exceptionRule.expectMessage(is("#: required key [servicePointId] not found"));
      schema.validate(example);
    }
  }
}
