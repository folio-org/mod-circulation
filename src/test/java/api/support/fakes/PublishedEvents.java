package api.support.fakes;

import static api.support.matchers.EventTypeMatchers.LOG_RECORD;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.vertx.core.json.JsonObject;

public class PublishedEvents extends ArrayList<JsonObject> {
  public static Predicate<JsonObject> byEventType(String eventType) {
    return evt -> eventType.equalsIgnoreCase(evt.getString("eventType"));
  }

  public static Predicate<JsonObject> byLogEventType(String logEventType) {
    final Predicate<JsonObject> byLogEventType = json ->
      json.getString("eventPayload").contains(logEventType);

    return byEventType(LOG_RECORD).and(byLogEventType);
  }

  public static Predicate<JsonObject> byLogAction(String action) {
    return json -> json.getString("eventPayload").contains(action);
  }

  public Stream<JsonObject> filter(Predicate<JsonObject> predicate) {
    return stream().filter(predicate);
  }

  public JsonObject findFirst(Predicate<JsonObject> predicate) {
    return filter(predicate).findFirst().orElse(new JsonObject());
  }

  public JsonObject getEventByType(String eventType) {
    return findFirst(byEventType(eventType));
  }
}
