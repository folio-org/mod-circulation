package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.NoticePolicyBuilder;
import api.support.http.ResourceClient;

public class NoticePoliciesFixture {
  private final RecordCreator noticePolicyRecordCreator;

  public NoticePoliciesFixture(ResourceClient noticePoliciesClient) {
    noticePolicyRecordCreator = new RecordCreator(noticePoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource inactiveNotice()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final NoticePolicyBuilder inactiveNoticePolicy = new NoticePolicyBuilder();

    return noticePolicyRecordCreator.createIfAbsent(inactiveNoticePolicy);
  }

  public IndividualResource activeNotice()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final NoticePolicyBuilder activeNoticePolicy = new NoticePolicyBuilder()
      .active();

    return noticePolicyRecordCreator.createIfAbsent(activeNoticePolicy);
  }

  public IndividualResource create(NoticePolicyBuilder noticePolicy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    return noticePolicyRecordCreator.createIfAbsent(noticePolicy);
  }

}
