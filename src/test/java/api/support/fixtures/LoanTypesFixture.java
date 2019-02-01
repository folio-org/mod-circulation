package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanTypesFixture {
  private final RecordCreator loanTypeRecordCreator;

  public LoanTypesFixture(ResourceClient loanTypesClient) {
    loanTypeRecordCreator = new RecordCreator(loanTypesClient,
      loanType -> getProperty(loanType, "name"));
  }

  public IndividualResource readingRoom()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loanTypeRecordCreator.createIfAbsent(loanType("Reading Room"));
  }

  public IndividualResource canCirculate()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return loanTypeRecordCreator.createIfAbsent(loanType("Can Circulate"));
  }

  private JsonObject loanType(String name) {
    final JsonObject loanType = new JsonObject();

    write(loanType, "name", name);

    return loanType;
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    loanTypeRecordCreator.cleanUp();
  }
}
