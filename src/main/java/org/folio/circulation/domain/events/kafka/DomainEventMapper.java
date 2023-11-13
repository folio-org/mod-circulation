package org.folio.circulation.domain.events.kafka;

import java.util.function.Function;

import io.vertx.core.json.JsonObject;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DomainEventMapper {

  public static <T> DomainEvent<T> toDomainEvent(String eventJsonString,
    Function<JsonObject, T> dataMapper) {

    final JsonObject eventJson = new JsonObject(eventJsonString);

    return new DomainEvent<>(
      eventJson.getString("id"),
      eventJson.getString("tenant"),
      DomainEventPayloadType.valueOf(eventJson.getString("type")),
      eventJson.getLong("timestamp"),
      dataMapper.apply(eventJson.getJsonObject("data"))
    );
  }

  public static DomainEvent<EntityChangedEventData> toEntityChangedEvent(String eventJsonString) {
    return toDomainEvent(eventJsonString, data ->
      new EntityChangedEventData(data.getJsonObject("old"), data.getJsonObject("new")));
  }
}
