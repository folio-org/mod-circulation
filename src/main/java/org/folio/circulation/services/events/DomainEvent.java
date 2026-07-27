package org.folio.circulation.services.events;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class DomainEvent<T> {

  UUID id;
  DomainEventType type;
  String tenant;
  long timestamp;
  T data;

}
