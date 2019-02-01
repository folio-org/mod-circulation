package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class AddressTypesFixture {
  private final RecordCreator addressTypeRecordCreator;

  public AddressTypesFixture(ResourceClient addressTypesClient) {
    addressTypeRecordCreator = new RecordCreator(addressTypesClient,
      addressType -> getProperty(addressType, "addressType"));
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    addressTypeRecordCreator.cleanUp();
  }

  public IndividualResource work()
      throws InterruptedException, 
      MalformedURLException, 
      TimeoutException, 
      ExecutionException {

    final JsonObject workAddressType = new JsonObject();

    write(workAddressType, "addressType", "Work");
    write(workAddressType, "desc", "Work address type");

    return addressTypeRecordCreator.createIfAbsent(workAddressType);
  }
}
