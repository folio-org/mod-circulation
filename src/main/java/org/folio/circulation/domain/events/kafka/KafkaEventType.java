package org.folio.circulation.domain.events.kafka;

import static org.folio.circulation.domain.events.kafka.CirculationStorageKafkaTopic.CIRCULATION_RULES;
import static org.folio.circulation.domain.events.kafka.EventPayloadType.UPDATE;
import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KafkaEventType {
  CIRCULATION_RULES_UPDATED(CIRCULATION_RULES, UPDATE);

  private final KafkaTopic kafkaTopic;
  private final EventPayloadType payloadType;
}
