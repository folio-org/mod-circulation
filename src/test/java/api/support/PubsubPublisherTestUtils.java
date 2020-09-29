package api.support;

import api.support.fakes.FakePubSub;
import api.support.matchers.EventMatchers;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

import static api.support.matchers.EventTypeMatchers.LOG_RECORD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class PubsubPublisherTestUtils {
  private PubsubPublisherTestUtils(){
  }

  public static void assertThatLogRecordEventsCountIsEqualTo(int messagesCount) {
    int logRecordEventsCount = FakePubSub.getPublishedEvents().stream()
      .filter(json -> LOG_RECORD.equals(json.getString("eventType")))
      .map(e -> 1)
      .reduce(0, Integer::sum);
    assertThat(logRecordEventsCount, equalTo(messagesCount));
  }

  public static void assertThatPublishedLogRecordEventsAreValid() {
    getPublishedLogRecordEvents().forEach(EventMatchers::isValidNoticeLogRecordEvent);
  }

  private static List<JsonObject> getPublishedLogRecordEvents() {
    return FakePubSub.getPublishedEvents().stream()
      .filter(json -> LOG_RECORD.equals(json.getString("eventType")))
      .collect(Collectors.toList());
  }
}
