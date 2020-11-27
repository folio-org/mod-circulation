package api.support.data.events.log;

import io.vertx.core.json.JsonObject;

public class JsonToCheckInLogEventMapper {
  public CheckInLogEvent fromJson(JsonObject logEventPayload) {
    return new CheckInLogEvent();
  }
}
