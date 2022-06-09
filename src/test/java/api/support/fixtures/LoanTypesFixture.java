package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import api.support.http.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LoanTypesFixture {
  private final RecordCreator loanTypeRecordCreator;

  public LoanTypesFixture(ResourceClient loanTypesClient) {
    loanTypeRecordCreator = new RecordCreator(loanTypesClient,
      loanType -> getProperty(loanType, "name"));
  }

  public IndividualResource readingRoom() {

    return loanTypeRecordCreator.createIfAbsent(loanType("Reading Room"));
  }

  public IndividualResource canCirculate() {

    return loanTypeRecordCreator.createIfAbsent(loanType("Can Circulate"));
  }

  private JsonObject loanType(String name) {
    final JsonObject loanType = new JsonObject();

    write(loanType, "name", name);

    return loanType;
  }

  public void cleanUp() {

    loanTypeRecordCreator.cleanUp();
  }
}
