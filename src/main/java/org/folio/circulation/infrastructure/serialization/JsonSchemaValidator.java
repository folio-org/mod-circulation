package org.folio.circulation.infrastructure.serialization;

import static java.util.stream.Collectors.collectingAndThen;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class JsonSchemaValidator {
  private final Schema schema;

  private JsonSchemaValidator(Schema schema) {
    this.schema = schema;
  }

  public static JsonSchemaValidator fromResource(String path) throws IOException {
    final Schema schema = getSchema(path);

    return new JsonSchemaValidator(schema);
  }

  private static Schema getSchema(String path) throws IOException {
    try (InputStream inputStream = JsonSchemaValidator.class
        .getResourceAsStream(path)) {

      JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));

      SchemaLoader schemaLoader = SchemaLoader.builder()
        .schemaClient(SchemaClient.classPathAwareClient())
        .schemaJson(rawSchema)
        .resolutionScope("classpath:///") // setting the default resolution scope
        .build();

      return schemaLoader.load().build();
    }
  }

  Result<String> validate(String json) {
    try {
      schema.validate(new JSONObject(json));

      return succeeded(json);
    }
    catch (ValidationException e) {
      if(e.getViolationCount() == 1) {
        return failedValidation(toValidationError(e));
      }

      return e.getCausingExceptions()
        .stream()
        .map(this::toValidationError)
        .collect(collectingAndThen(Collectors.toList(),
          ValidationErrorFailure::failedValidation));
    }
    catch(JSONException e) {
      return failed(new BadRequestFailure(
        String.format("Cannot parse \"%s\" as JSON", json)));
    }
    catch(Exception e) {
      return failedDueToServerError(e);
    }
  }

  private ValidationError toValidationError(ValidationException validationException) {
    return new ValidationError(validationException.getMessage(), null, null);
  }
}
