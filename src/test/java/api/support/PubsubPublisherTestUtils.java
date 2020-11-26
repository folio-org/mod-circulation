package api.support;

import static api.support.fakes.PublishedEvents.byLogEventType;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;

import java.util.List;
import java.util.function.Predicate;

import api.support.fakes.FakePubSub;
import api.support.matchers.EventMatchers;
import io.vertx.core.json.JsonObject;

public class PubsubPublisherTestUtils {
  private PubsubPublisherTestUtils() { }

  public static void assertThatPublishedLoanLogRecordEventsAreValid() {
    getPublishedEvents(byLogEventType(LOAN.value())).forEach(EventMatchers::isValidLoanLogRecordEvent);
  }

  public static void assertThatPublishedLogRecordEventsAreValid() {
    getPublishedEvents(byLogEventType(NOTICE.value())).forEach(EventMatchers::isValidNoticeLogRecordEvent);
  }

  public static List<JsonObject> getPublishedEvents(Predicate<JsonObject> predicate) {
    return FakePubSub.getPublishedEvents().filterToList(predicate);
  }
}
