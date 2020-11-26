package api.support.fakes;

import static api.support.matchers.EventTypeMatchers.LOG_RECORD;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.vertx.core.json.JsonObject;

public class PublishedEvents extends ArrayList<JsonObject> {
  public static Predicate<JsonObject> byEventType(String eventType) {
    return evt -> eventType.equalsIgnoreCase(evt.getString("eventType"));
  }

  public static Predicate<JsonObject> byLogEventType(String logEventType) {
    final Predicate<JsonObject> byLogEventType = json ->
      new JsonObject(json.getString("eventPayload")).getString("logEventType")
        .equals(logEventType);

    return byEventType(LOG_RECORD).and(byLogEventType);
  }

  public static Predicate<JsonObject> byLogAction(String action) {
    return json -> json.getString("eventPayload").contains(action);
  }

  public static Predicate<JsonObject> byLogEventTypeAndAction(String type, String action) {
    return byLogEventType(type).and(byLogAction(action));
  }

  public Stream<JsonObject> filter(Predicate<JsonObject> predicate) {
    return stream().filter(predicate);
  }

  public List<JsonObject> filterToList(Predicate<JsonObject> predicate) {
    return filter(predicate).collect(toList());
  }

  public JsonObject findFirst(Predicate<JsonObject> predicate) {
    return filter(predicate).findFirst()
      .orElseThrow(() -> new RuntimeException("No event found for filter"));
  }
}
