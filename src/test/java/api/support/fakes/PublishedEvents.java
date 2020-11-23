package api.support.fakes;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.vertx.core.json.JsonObject;

public class PublishedEvents extends ArrayList<JsonObject> {
  public static Predicate<JsonObject> byEventType(String eventType) {
    return evt -> eventType.equalsIgnoreCase(evt.getString("eventType"));
  }

  public JsonObject getEventByType(String eventType) {
    return filterEventsByType(eventType)
      .findFirst().orElse(new JsonObject());
  }

  public Stream<JsonObject> filterEventsByType(String eventType) {
    return filter(byEventType(eventType));
  }

  public Stream<JsonObject> filter(Predicate<JsonObject> predicate) {
    return stream().filter(predicate);
  }
}
