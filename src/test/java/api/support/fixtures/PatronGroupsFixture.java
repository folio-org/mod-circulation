package api.support.fixtures;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.JsonPropertyFetcher;
import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;

public class PatronGroupsFixture {
  private final RecordCreator patronGroupRecordCreator;

  public PatronGroupsFixture(ResourceClient patronGroupsClient) {
    patronGroupRecordCreator = new RecordCreator(patronGroupsClient,
      group -> JsonPropertyFetcher.getProperty(group, "group"));
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    patronGroupRecordCreator.cleanUp();
  }

  public IndividualResource regular()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.regular());
  }

  public IndividualResource alternative()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.alternative());
  }

  public IndividualResource staff()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.staff());
  }

  public IndividualResource faculty()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.faculty());
  }
}
