package api.support.fakes;

import java.io.IOException;

import org.folio.circulation.infrastructure.serialization.JsonSchemaValidator;

public class StorageSchema {
  private StorageSchema() { }

  public static JsonSchemaValidator validatorForStorageItemSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/item-storage-8-9.json");
  }

  public static JsonSchemaValidator validatorForStorageLoanSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/storage-loan-7-2.json");
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

  public static JsonSchemaValidator validatorForCheckInStorageSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/check-in-storage-0-2.json");
  }

  public static JsonSchemaValidator validatorForNoteSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/note-2-0.json");
  }

  public static JsonSchemaValidator validatorForFeeFineOperationSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/fee-fine-operation-16-1.json");
  }

  public static JsonSchemaValidator validatorForFeeFineCancelOperationSchema() throws IOException {
    return JsonSchemaValidator.fromResource("/fee-fine-cancel-operation-16-1.json");
  }
}
