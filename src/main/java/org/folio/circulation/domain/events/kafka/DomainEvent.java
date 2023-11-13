package org.folio.circulation.domain.events.kafka;

public record DomainEvent<T> (
  String id,
  String tenantId,
  DomainEventPayloadType payloadType,
  long timestamp,
  T data
) { }


