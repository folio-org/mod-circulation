package org.folio.circulation.infrastructure.serialization;

import java.io.IOException;
import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
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

  void validate(String json) {
    schema.validate(new JSONObject(json));
  }
}
