package api.support.fakes;

import java.io.IOException;

import org.folio.circulation.infrastructure.serialization.JsonSchemaValidator;

public class StorageSchema {
  private StorageSchema() { }

  public static JsonSchemaValidator validatorForStorageLoanSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/storage-loan-6-1.json");
  }
}
