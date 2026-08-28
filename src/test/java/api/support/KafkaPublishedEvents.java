package api.support;

import static api.support.fakes.PublishedEvents.byLogEventType;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import api.support.fakes.PublishedEvents;
import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaPublishedEvents {
  @Getter
  private static final PublishedEvents publishedEvents = new PublishedEvents();

  public static <T> T findFirstLogEvent(String eventType, Function<JsonObject, T> payloadMapper) {
    final var publishedEvent = publishedEvents.findFirst(byLogEventType(eventType));
    final var logEventPayload = new JsonObject(getProperty(publishedEvent, "eventPayload"));

    return payloadMapper.apply(logEventPayload);
  }

  public static List<JsonObject> getPublishedEventsAsList(Predicate<JsonObject> predicate) {
    return publishedEvents.filterToList(predicate);
  }

  public static void recordPublishedEvent(String eventType, String eventPayload) {
    publishedEvents.add(new JsonObject()
      .put("eventType", eventType)
      .put("eventPayload", eventPayload));
  }

  public static void clearPublishedEvents() {
    publishedEvents.clear();
  }
}
