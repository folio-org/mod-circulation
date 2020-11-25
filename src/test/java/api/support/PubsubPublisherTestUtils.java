package api.support;

import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.matchers.EventTypeMatchers.LOG_RECORD;
import static java.util.stream.Collectors.toList;
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

  public static Predicate<JsonObject> byLogEventType(String logEventType) {
    final Predicate<JsonObject> byLogEventType = json ->
      json.getString("eventPayload").contains(logEventType);

    return byEventType(LOG_RECORD).and(byLogEventType);
  }

  public static Predicate<JsonObject> byLogAction(String action) {
    return json -> json.getString("eventPayload").contains(action);
  }

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
    return filterToList(byLogEventType(logEventType));
  }

  public static List<JsonObject> getPublishedLogRecordEvents(String logEventType, String action) {
    return filterToList(byLogEventType(logEventType).and(byLogAction(action)));
  }

  private static List<JsonObject> filterToList(Predicate<JsonObject> predicate) {
    return FakePubSub.getPublishedEvents().filter(predicate).collect(toList());
  }
}
