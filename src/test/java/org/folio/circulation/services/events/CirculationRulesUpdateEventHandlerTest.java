package org.folio.circulation.services.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

@ExtendWith(MockitoExtension.class)
class CirculationRulesUpdateEventHandlerTest {
  @Mock
  private KafkaConsumerRecord<String, String> consumerRecord;

  @Test
  void skipsInvalidEventWithoutFailingRecordProcessing() {
    when(consumerRecord.key()).thenReturn("event-key");
    when(consumerRecord.value()).thenReturn(new JsonObject()
      .put("id", "event-id")
      .put("type", "UPDATED")
      .put("timestamp", System.currentTimeMillis())
      .put("data", new JsonObject())
      .encode());

    Future<String> result = new CirculationRulesUpdateEventHandler().handle(consumerRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is("event-key"));
  }
}
