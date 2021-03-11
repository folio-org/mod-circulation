package api.support;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.matchers.EventMatchers.isValidAnonymizeLoansLogRecordEvent;
import static api.support.matchers.EventMatchers.isValidLoanLogRecordEvent;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;

public class PubsubPublisherTestUtils {
  private PubsubPublisherTestUtils() { }

  public static void assertThatPublishedLoanLogRecordEventsAreValid(JsonObject loan) {
    getPublishedEventsAsList(byLogEventType(LOAN.value())).stream()
      .filter(event -> event.getString("eventPayload").contains(loan.getString("id")))
      .forEach(event -> assertThat(event, isValidLoanLogRecordEvent(loan))
    );
  }

  public static void assertThatPublishedAnonymizeLoanLogRecordEventsAreValid(JsonObject loan) {
    getPublishedEventsAsList(byLogEventType(LOAN.value())).stream()
      .filter(event -> event.getString("eventPayload").contains(loan.getString("id")))
      .forEach(event -> assertThat(event, isValidAnonymizeLoansLogRecordEvent(loan))
    );
  }
}
