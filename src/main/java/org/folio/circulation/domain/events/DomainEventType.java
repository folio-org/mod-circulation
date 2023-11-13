package org.folio.circulation.domain.events;

import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DomainEventType {
  CIRCULATION_RULES_UPDATED(
    CirculationStorageKafkaTopic.CIRCULATION_RULES, DomainEventPayloadType.UPDATED);

  private final KafkaTopic kafkaTopic;
  private final DomainEventPayloadType payloadType;
}
