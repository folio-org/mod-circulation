package api.support.fixtures;

import static api.support.fixtures.AddressExamples.HOME_ADDRESS_TYPE;
import static api.support.fixtures.AddressExamples.WORK_ADDRESS_TYPE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import api.support.http.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class AddressTypesFixture {
  private final RecordCreator addressTypeRecordCreator;

  public AddressTypesFixture(ResourceClient addressTypesClient) {
    addressTypeRecordCreator = new RecordCreator(addressTypesClient,
      addressType -> getProperty(addressType, "addressType"));
  }

  public void cleanUp() {

    addressTypeRecordCreator.cleanUp();
  }

  public IndividualResource work() {

    final JsonObject workAddressType = new JsonObject();

    write(workAddressType, "addressType", "Work");
    write(workAddressType, "desc", "Work address type");
    write(workAddressType, "id", WORK_ADDRESS_TYPE);

    return addressTypeRecordCreator.createIfAbsent(workAddressType);
  }

  public IndividualResource home() {

    final JsonObject homeAddressType = new JsonObject();

    write(homeAddressType, "addressType", "Home");
    write(homeAddressType, "desc", "Home address type");
    write(homeAddressType, "id", HOME_ADDRESS_TYPE);

    return addressTypeRecordCreator.createIfAbsent(homeAddressType);
  }
}
