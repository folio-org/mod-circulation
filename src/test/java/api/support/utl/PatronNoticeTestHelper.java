package api.support.utl;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.domain.representations.logs.LogEventType;

import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.http.CqlQuery;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class PatronNoticeTestHelper {

  private static final ResourceClient scheduledNoticesClient =
    ResourceClient.forScheduledNotices();

  private static final ResourceClient patronActionSessionsClient =
    ResourceClient.forPatronSessionRecords();


  private PatronNoticeTestHelper() {}

  public static List<JsonObject> verifyNumberOfScheduledNotices(int numberOfNotices) {
    return waitForSize(scheduledNoticesClient::getAll, numberOfNotices);
  }

  public static List<JsonObject> verifyNumberOfSentNotices(int numberOfNotices) {
    return waitForSize(FakeModNotify::getSentPatronNotices, numberOfNotices);
  }

  public static List<JsonObject> verifyNumberOfPublishedEvents(LogEventType eventType, int eventsCount) {
    return waitForSize(() -> getPublishedEventsAsList(byLogEventType(eventType)), eventsCount);
  }

  public static List<JsonObject> verifyNumberOfExistingActionSessions(int numberOfNotices) {
    return waitForSize(patronActionSessionsClient::getAll, numberOfNotices);
  }

  public static List<JsonObject> verifyNumberOfExistingActionSessions(int numberOfNotices,
    PatronActionType actionType) {

    return waitForSize(
      () -> patronActionSessionsClient.getAll()
      .stream()
      .filter(json -> StringUtils.equals(actionType.getRepresentation(), json.getString(ACTION_TYPE)))
      .collect(Collectors.toList()),
      numberOfNotices
    );
  }

  private static List<JsonObject> waitForSize(Callable<List<JsonObject>> supplier, int expectedSize) {
    return waitAtMost(1, SECONDS)
      .until(supplier, hasSize(expectedSize));
  }

  public static void clearSentPatronNoticesAndPubsubEvents() {
    FakeModNotify.clearSentPatronNotices();
    FakePubSub.clearPublishedEvents();
  }
}
