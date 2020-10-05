package api.support.spring;

import static api.support.matchers.EventTypeMatchers.LOG_RECORD;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadType.LOAN;

import api.support.fakes.FakePubSub;
import api.support.matchers.EventMatchers;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

public class PubsubPublisherTestUtils {
  public static void assertThatPublishedLoanLogRecordEventsAreValid() {
    getPublishedLogRecordEvents(LOAN.value()).forEach(EventMatchers::isValidLoanLogRecordEvent);
  }

  private static List<JsonObject> getPublishedLogRecordEvents(String logEventType) {
    return FakePubSub.getPublishedEvents().stream()
      .filter(json -> LOG_RECORD.equals(json.getString("eventType")) &&
        json.getString("eventPayload").contains(logEventType))
      .collect(Collectors.toList());
  }
}
