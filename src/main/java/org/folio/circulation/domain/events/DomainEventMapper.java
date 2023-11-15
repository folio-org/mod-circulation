package org.folio.circulation.domain.events;

import static java.util.Objects.requireNonNull;

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
      requireNonNull(eventJson.getString("tenant")),
      requireNonNull(DomainEventPayloadType.valueOf(eventJson.getString("type"))),
      requireNonNull(eventJson.getLong("timestamp")),
      dataMapper.apply(requireNonNull(eventJson.getJsonObject("data")))
    );
  }

  public static DomainEvent<EntityChangedEventData> toEntityChangedEvent(String eventJsonString) {
    return toDomainEvent(eventJsonString, data ->
      new EntityChangedEventData(
        requireNonNull(data.getJsonObject("old")),
        requireNonNull(data.getJsonObject("new"))));
  }
}
