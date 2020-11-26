package api.support;

import static api.support.fakes.PublishedEvents.byLogAction;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.function.Predicate;

import api.support.fakes.FakePubSub;
import api.support.matchers.EventMatchers;
import io.vertx.core.json.JsonObject;

public class PubsubPublisherTestUtils {
  private PubsubPublisherTestUtils() { }

  public static void assertThatPublishedLoanLogRecordEventsAreValid() {
    getPublishedLogRecordEvents(LOAN.value()).forEach(EventMatchers::isValidLoanLogRecordEvent);
  }

  public static void assertThatPublishedLogRecordEventsAreValid() {
    getPublishedLogRecordEvents(NOTICE.value()).forEach(EventMatchers::isValidNoticeLogRecordEvent);
  }

  public static void assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(int messagesCount) {
    assertThat(getPublishedLogRecordEvents(NOTICE.value()), hasSize(messagesCount));
  }

  public static List<JsonObject> getPublishedLogRecordEvents(String logEventType) {
    return getPublishedEvents(byLogEventType(logEventType));
  }

  public static List<JsonObject> getPublishedLogRecordEvents(String logEventType, String action) {
    return getPublishedEvents(byLogEventType(logEventType).and(byLogAction(action)));
  }

  public static List<JsonObject> getPublishedEvents(Predicate<JsonObject> predicate) {
    return FakePubSub.getPublishedEvents().filterToList(predicate);
  }
}
