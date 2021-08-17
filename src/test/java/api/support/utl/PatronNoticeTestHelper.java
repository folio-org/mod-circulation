package api.support.utl;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.concurrent.Callable;

import org.folio.circulation.domain.representations.logs.LogEventType;

import api.support.fakes.FakePubSub;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class PatronNoticeTestHelper {

  private static final ResourceClient scheduledNoticesClient =
    ResourceClient.forScheduledNotices();

  private static final ResourceClient patronNoticesClient =
    ResourceClient.forPatronNotices();

  private PatronNoticeTestHelper() {}

  public static List<JsonObject> verifyNumberOfScheduledNotices(int numberOfNotices) {
    return waitForSize(scheduledNoticesClient::getAll, numberOfNotices);
  }

  public static List<JsonObject> verifyNumberOfSentNotices(int numberOfNotices) {
    return waitForSize(patronNoticesClient::getAll, numberOfNotices);
  }

  public static List<JsonObject> verifyNumberOfPublishedEvents(LogEventType eventType, int eventsCount) {
    return waitForSize(() -> getPublishedEventsAsList(byLogEventType(eventType)), eventsCount);
  }

  private static List<JsonObject> waitForSize(Callable<List<JsonObject>> supplier, int expectedSize) {
    return waitAtMost(1, SECONDS)
      .until(supplier, hasSize(expectedSize));
  }

  public static void clearSentPatronNoticesAndPubsubEvents() {
    patronNoticesClient.deleteAll();
    FakePubSub.clearPublishedEvents();
  }

}
