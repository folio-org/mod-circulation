package org.folio.circulation.domain.events;

import static org.folio.circulation.domain.events.CirculationStorageKafkaTopic.CIRCULATION_RULES;
import static org.folio.circulation.domain.events.DomainEventPayloadType.UPDATED;

import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DomainEventType {
  CIRCULATION_RULES_UPDATED(CIRCULATION_RULES, UPDATED);

  private final KafkaTopic kafkaTopic;
  private final DomainEventPayloadType payloadType;
}
