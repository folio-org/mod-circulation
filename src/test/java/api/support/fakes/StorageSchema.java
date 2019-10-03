package api.support.fakes;

import java.io.IOException;

import org.folio.circulation.infrastructure.serialization.JsonSchemaValidator;

public class StorageSchema {
  private StorageSchema() { }

  public static JsonSchemaValidator validatorForStorageLoanSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/storage-loan-6-2.json");
  }

  public static JsonSchemaValidator validatorForLocationInstSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/locinst.json");
  }

  public static JsonSchemaValidator validatorForLocationCampSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/loccamp.json");
  }

  public static JsonSchemaValidator validatorForLocationLibSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/loclib.json");
  }
}
