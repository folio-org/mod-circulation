package api.support.utl;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.concurrent.Callable;

import org.folio.circulation.domain.representations.logs.LogEventType;

import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class PatronNoticeTestHelper {

  private static final ResourceClient scheduledNoticesClient =
    ResourceClient.forScheduledNotices();

  private PatronNoticeTestHelper() {}

  public static void verifyNumberOfScheduledNotices(int numberOfNotices) {
    waitForSize(scheduledNoticesClient::getAll, numberOfNotices);
  }

  public static void verifyNumberOfSentNotices(int numberOfNotices) {
    waitForSize(FakeModNotify::getSentPatronNotices, numberOfNotices);
  }

  public static void verifyNumberOfPublishedEvents(LogEventType eventType, int eventsCount) {
    waitForSize(() -> getPublishedEventsAsList(byLogEventType(eventType)), eventsCount);
  }

  private static void waitForSize(Callable<List<JsonObject>> supplier, int expectedSize) {
    waitAtMost(1, SECONDS)
      .until(supplier, hasSize(expectedSize));
  }

  public static void clearSentPatronNoticesAndPubsubEvents() {
    FakeModNotify.clearSentPatronNotices();
    FakePubSub.clearPublishedEvents();
  }
}
