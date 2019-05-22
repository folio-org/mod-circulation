package org.folio.circulation.infrastructure.serialization;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.io.IOException;
import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.folio.circulation.support.Result;
import org.json.JSONObject;
import org.json.JSONTokener;

class JsonSchemaValidator {
  private final Schema schema;

  private JsonSchemaValidator(Schema schema) {
    this.schema = schema;
  }

  static JsonSchemaValidator fromResource(String path) throws IOException {
    final Schema schema = getSchema(path);

    return new JsonSchemaValidator(schema);
  }

  private static Schema getSchema(String path) throws IOException {
    try (InputStream inputStream = JsonSchemaValidator.class
        .getResourceAsStream(path)) {

      JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));

      return SchemaLoader.load(rawSchema);
    }
  }

  Result<String> validate(String json) {
    try {
      schema.validate(new JSONObject(json));

      return Result.succeeded(json);
    }
    catch (ValidationException e) {
      return failedValidation(String.format(e.getMessage()), null, null);
    }
  }
}
