package org.folio.circulation.services.events;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.EventType.FEE_FINE_BALANCE_CHANGED;
import static org.folio.circulation.support.kafka.KafkaConfigConstants.DEFAULT_GATEWAY_URL;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.kafka.headers.FolioKafkaHeaders.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.WebContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

@ExtendWith(MockitoExtension.class)
class AbstractKafkaEventHandlerTest {
  @Mock
  private Context vertxContext;
  @Mock
  private HttpClient client;
  @Mock
  private KafkaEventProcessor processor;
  @Mock
  private KafkaConsumerRecord<String, String> consumerRecord;

  private AbstractKafkaEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new AbstractKafkaEventHandler(vertxContext, client, DEFAULT_GATEWAY_URL,
      FEE_FINE_BALANCE_CHANGED, processor) {};
    when(consumerRecord.key()).thenReturn("event-key");
    when(consumerRecord.value()).thenReturn(new JsonObject().put("id", "event-id").encode());
    when(consumerRecord.headers()).thenReturn(List.of(KafkaHeader.header(TENANT_ID, "tenant-id")));
  }

  @Test
  void returnsEventKeyWhenProcessingSucceeds() {
    when(processor.process(any(), any(), any())).thenReturn(completedFuture(succeeded(null)));

    Future<String> result = handler.handle(consumerRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), equalTo("event-key"));

    ArgumentCaptor<JsonObject> payloadCaptor = ArgumentCaptor.forClass(JsonObject.class);
    ArgumentCaptor<WebContext> contextCaptor = ArgumentCaptor.forClass(WebContext.class);
    verify(processor).process(payloadCaptor.capture(), contextCaptor.capture(), eq(client));
    assertThat(payloadCaptor.getValue().getString("id"), equalTo("event-id"));
    assertThat(contextCaptor.getValue().getTenantId(), equalTo("tenant-id"));
    assertThat(contextCaptor.getValue().getOkapiLocation(), equalTo(DEFAULT_GATEWAY_URL));
    assertThat(contextCaptor.getValue().getVertxContext(), is(vertxContext));
  }

  @Test
  void failsWhenProcessorReturnsFailure() {
    when(processor.process(any(), any(), any())).thenReturn(completedFuture(
      failed(new ServerErrorFailure("processor failed"))));

    Future<String> result = handler.handle(consumerRecord);

    assertThat(result.failed(), is(true));
    assertThat(result.cause().getMessage(), equalTo(
      "Server error failure, reason: processor failed"));
  }

  @Test
  void failsWithoutCallingProcessorWhenPayloadIsInvalid() {
    when(consumerRecord.value()).thenReturn("invalid-json");

    Future<String> result = handler.handle(consumerRecord);

    assertThat(result.failed(), is(true));
    verifyNoInteractions(processor);
  }
}
