package org.folio.circulation.services.events;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.folio.kafka.KafkaProducerManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherUnitTest {
  private static final String TOPIC = "folio.test.circulation.ITEM_CHECKED_OUT";

  @Mock
  private KafkaProducerManager producerManager;
  @Mock
  private KafkaProducer<String, String> producer;

  @Test
  void publishesRawPayloadWithoutDomainEventWrapper() {
    when(producerManager.<String, String>createShared(eq(TOPIC))).thenReturn(producer);
    when(producer.send(org.mockito.ArgumentMatchers.any())).thenReturn(Future.succeededFuture());
    when(producer.flush()).thenReturn(Future.succeededFuture());
    when(producer.close()).thenReturn(Future.succeededFuture());

    String payload = new JsonObject()
      .put("userId", "user-1")
      .put("loanId", "loan-1")
      .encode();
    Map<String, String> headers = Map.of(
      OKAPI_URL, "http://okapi:9130",
      TENANT, "test",
      TOKEN, "token");

    var result = new KafkaEventPublisher<String>(TOPIC, producerManager)
      .publish("key-1", payload, headers)
      .join();

    assertThat(result.succeeded(), is(true));

    ArgumentCaptor<KafkaProducerRecord<String, String>> recordCaptor =
      ArgumentCaptor.forClass(KafkaProducerRecord.class);
    verify(producer).send(recordCaptor.capture());

    KafkaProducerRecord<String, String> record = recordCaptor.getValue();
    assertThat(record.topic(), is(TOPIC));
    assertThat(record.key(), is("key-1"));
    assertThat(record.value(), is(payload));
    assertThat(record.value(), not(is(new JsonObject().put("data", new JsonObject(payload)).encode())));
    assertThat(record.headers().stream()
      .filter(header -> "folio.tenantId".equals(header.key()))
      .findFirst()
      .orElse(null), not(nullValue()));
  }
}
