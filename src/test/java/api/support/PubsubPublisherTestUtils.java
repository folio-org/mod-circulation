package api.support;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;

import api.support.matchers.EventMatchers;

public class PubsubPublisherTestUtils {
  private PubsubPublisherTestUtils() { }

  public static void assertThatPublishedLoanLogRecordEventsAreValid() {
    getPublishedEventsAsList(byLogEventType(LOAN.value())).forEach(EventMatchers::isValidLoanLogRecordEvent);
  }

  public static void assertThatPublishedLogRecordEventsAreValid() {
    getPublishedEventsAsList(byLogEventType(NOTICE.value())).forEach(EventMatchers::isValidNoticeLogRecordEvent);
  }
}
