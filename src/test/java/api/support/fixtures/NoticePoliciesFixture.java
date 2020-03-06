package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
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

  public IndividualResource inactiveNotice() {

    final NoticePolicyBuilder inactiveNoticePolicy = new NoticePolicyBuilder();

    return noticePolicyRecordCreator.createIfAbsent(inactiveNoticePolicy);
  }

  public IndividualResource activeNotice() {

    final NoticePolicyBuilder activeNoticePolicy = new NoticePolicyBuilder()
      .active();

    return noticePolicyRecordCreator.createIfAbsent(activeNoticePolicy);
  }

  public IndividualResource create(NoticePolicyBuilder noticePolicy) {
    return noticePolicyRecordCreator.createIfAbsent(noticePolicy);
  }

  public void create(List<String> ids) {
    ids.stream()
      .map(id -> new NoticePolicyBuilder()
        .withId(UUID.fromString(id))
        .withName("Example NoticePolicy " + id))
    .forEach(noticePolicyRecordCreator::createIfAbsent);
  }

  public void cleanUp() {
    noticePolicyRecordCreator.cleanUp();
  }
}
