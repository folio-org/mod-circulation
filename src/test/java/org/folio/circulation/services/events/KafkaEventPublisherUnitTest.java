package org.folio.circulation.services.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.DEFAULT_GATEWAY_URL;
import static org.folio.kafka.headers.FolioKafkaHeaders.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.CompletionException;

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
  private static final String TENANT_ID_VALUE = "test";

  @Mock
  private KafkaProducerManager producerManager;
  @Mock
  private KafkaProducer<String, String> producer;

  @Test
  void publishesRawPayloadWithoutEnvelope() {
    when(producerManager.<String, String>createShared(TOPIC)).thenReturn(producer);
    when(producer.send(any())).thenReturn(Future.succeededFuture());
    when(producer.flush()).thenReturn(Future.succeededFuture());
    when(producer.close()).thenReturn(Future.succeededFuture());

    JsonObject payload = new JsonObject()
      .put("userId", "user-1")
      .put("loanId", "loan-1")
      .put("item", new JsonObject().put("status", "Checked out"));
    Map<String, String> headers = Map.of(
      OKAPI_URL, DEFAULT_GATEWAY_URL,
      TENANT, "test",
      TOKEN, "token");

    var result = new KafkaEventPublisher<String>(TOPIC, TENANT_ID_VALUE, producerManager)
      .publish("key-1", payload, headers)
      .join();

    assertThat(result.succeeded(), is(true));

    ArgumentCaptor<KafkaProducerRecord<String, String>> recordCaptor =
      ArgumentCaptor.forClass(KafkaProducerRecord.class);
    verify(producer).send(recordCaptor.capture());

    KafkaProducerRecord<String, String> producerRecord = recordCaptor.getValue();
    assertThat(producerRecord.topic(), is(TOPIC));
    assertThat(producerRecord.key(), is("key-1"));
    assertThat(producerRecord.value(), is(payload.encode()));
    assertThat(producerRecord.value(),
      not(is(new JsonObject().put("data", payload).encode())));
    assertThat(tenantIdHeaderValue(producerRecord), is("test"));
  }

  @Test
  void usesConfiguredTenantIdForKafkaHeader() {
    when(producerManager.<String, String>createShared(TOPIC)).thenReturn(producer);
    when(producer.send(any())).thenReturn(Future.succeededFuture());
    when(producer.flush()).thenReturn(Future.succeededFuture());
    when(producer.close()).thenReturn(Future.succeededFuture());

    JsonObject payload = new JsonObject()
      .put("userId", "user-1")
      .put("loanId", "loan-1");
    Map<String, String> headers = Map.of(
      OKAPI_URL.toLowerCase(), DEFAULT_GATEWAY_URL,
      TENANT.toLowerCase(), "different-tenant",
      TOKEN.toLowerCase(), "token");

    new KafkaEventPublisher<String>(TOPIC, TENANT_ID_VALUE, producerManager)
      .publish("key-1", payload, headers)
      .join();

    ArgumentCaptor<KafkaProducerRecord<String, String>> recordCaptor =
      ArgumentCaptor.forClass(KafkaProducerRecord.class);
    verify(producer).send(recordCaptor.capture());

    assertThat(tenantIdHeaderValue(recordCaptor.getValue()), is("test"));
  }

  @Test
  void failsWhenKafkaSendFails() {
    var failure = new IllegalStateException("Kafka is unreachable");
    when(producerManager.<String, String>createShared(TOPIC)).thenReturn(producer);
    when(producer.send(any())).thenReturn(Future.failedFuture(failure));
    when(producer.flush()).thenReturn(Future.succeededFuture());
    when(producer.close()).thenReturn(Future.succeededFuture());

    var publishFuture = new KafkaEventPublisher<String>(TOPIC, TENANT_ID_VALUE, producerManager)
      .publish("key-1", new JsonObject(), Map.of(TENANT, "test"));
    var error = assertThrows(CompletionException.class, publishFuture::join);

    assertThat(error.getCause(), sameInstance(failure));
    verify(producer).exceptionHandler(any());
  }

  private static String tenantIdHeaderValue(KafkaProducerRecord<String, String> producerRecord) {
    return producerRecord.headers().stream()
      .filter(header -> TENANT_ID.equals(header.key()))
      .findFirst()
      .map(header -> header.value().toString(UTF_8))
      .orElse(null);
  }
}
