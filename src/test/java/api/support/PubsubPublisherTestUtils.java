package api.support;

import api.support.fakes.FakePubSub;
import api.support.matchers.EventMatchers;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

import static api.support.matchers.EventTypeMatchers.LOG_RECORD;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadType.NOTICE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class PubsubPublisherTestUtils {
  private PubsubPublisherTestUtils(){
  }

  public static void assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(int messagesCount) {
    assertThat(getPublishedLogRecordEvents(NOTICE.value()).size(), equalTo(messagesCount));
  }

  public static void assertThatPublishedLogRecordEventsAreValid() {
    getPublishedLogRecordEvents(NOTICE.value()).forEach(EventMatchers::isValidNoticeLogRecordEvent);
  }

  private static List<JsonObject> getPublishedLogRecordEvents(String logEventType) {
    return FakePubSub.getPublishedEvents().stream()
      .filter(json -> LOG_RECORD.equals(json.getString("eventType")) &&
        json.getString("eventPayload").contains(logEventType))
      .collect(Collectors.toList());
  }
}
