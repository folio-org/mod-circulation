package org.folio.circulation.infrastructure.serialization;

import org.everit.json.schema.Schema;
import org.json.JSONObject;

class JsonSchemaValidator {
  private final Schema schema;

  JsonSchemaValidator(Schema schema) {
    this.schema = schema;
  }

  void validate(String json) {
    schema.validate(new JSONObject(json));
  }
}
