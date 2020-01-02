package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.InstanceBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class InstancesFixture {
  private final RecordCreator instanceTypeRecordCreator;
  private final RecordCreator contributorNameTypeRecordCreator;
  private final ResourceClient instancesClient;

  public InstancesFixture(
      ResourceClient instanceTypesClient,
      ResourceClient contributorNameTypesClient) {

    instanceTypeRecordCreator = new RecordCreator(instanceTypesClient,
      instanceType -> getProperty(instanceType, "name"));

    contributorNameTypeRecordCreator = new RecordCreator(
      contributorNameTypesClient, nameType -> getProperty(nameType, "name"));

    instancesClient =  ResourceClient.forInstances();
  }

  public void cleanUp() {

    instanceTypeRecordCreator.cleanUp();
    contributorNameTypeRecordCreator.cleanUp();
  }

  public IndividualResource basedUponDunkirk() {

    InstanceBuilder builder = new InstanceBuilder("Dunkirk", booksInstanceTypeId())
      .withContributor("Novik, Naomi", getPersonalContributorNameTypeId());

    return instancesClient.create(builder);
  }


  private UUID getPersonalContributorNameTypeId() {

    final JsonObject personalName = new JsonObject();

    write(personalName, "name", "Personal name");

    return contributorNameTypeRecordCreator.createIfAbsent(personalName).getId();
  }

  private UUID booksInstanceTypeId() {

    final JsonObject booksInstanceType = new JsonObject();

    write(booksInstanceType, "name", "Books");
    write(booksInstanceType, "code", "BO");
    write(booksInstanceType, "source", "tests");

    return instanceTypeRecordCreator.createIfAbsent(booksInstanceType).getId();
  }

}
