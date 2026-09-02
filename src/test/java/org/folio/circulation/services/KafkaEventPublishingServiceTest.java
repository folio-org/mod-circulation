package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_IN;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.results.Result.failed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.services.events.KafkaEventPublisher;
import org.folio.circulation.services.events.KafkaService;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.WebContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublishingServiceTest {
  private static final String TENANT_ID = "test_tenant";
  private static final JsonObject PAYLOAD = new JsonObject();
  private static final Map<String, String> HEADERS = Map.of(TENANT, TENANT_ID);

  @Mock
  private Context vertxContext;
  @Mock
  private Vertx vertx;
  @Mock
  private WebContext webContext;
  @Mock
  private KafkaService kafkaService;
  @Mock
  private KafkaEventPublisher<String> publisher;

  @Test
  void createsServiceUsingContextFromWebContext() {
    when(webContext.getHeaders()).thenReturn(HEADERS);
    when(webContext.getVertxContext()).thenReturn(vertxContext);
    when(vertxContext.owner()).thenReturn(vertx);

    assertDoesNotThrow(() -> new KafkaEventPublishingService(webContext));
  }

  @Test
  void failsWhenKafkaPublisherFails() {
    var failure = new IllegalStateException("Kafka publish failed");
    when(kafkaService.createPublisher(CirculationKafkaTopic.ITEM_CHECKED_IN, vertxContext,
      TENANT_ID)).thenReturn(publisher);
    when(publisher.publish(anyString(), eq(PAYLOAD), eq(HEADERS)))
      .thenReturn(failedFuture(failure));

    var service = new KafkaEventPublishingService(HEADERS, vertxContext, kafkaService);
    var publishFuture = service.publishEvent(ITEM_CHECKED_IN.name(), PAYLOAD);

    var error = assertThrows(CompletionException.class, publishFuture::join);

    assertThat(error.getCause(), instanceOf(IllegalStateException.class));
    assertThat(error.getCause(), sameInstance(failure));
  }

  @Test
  void failsWhenKafkaPublisherReturnsFailure() {
    when(kafkaService.createPublisher(CirculationKafkaTopic.ITEM_CHECKED_IN, vertxContext,
      TENANT_ID)).thenReturn(publisher);
    when(publisher.publish(anyString(), eq(PAYLOAD), eq(HEADERS)))
      .thenReturn(CompletableFuture.completedFuture(
        failed(new ServerErrorFailure("Kafka publish failed"))));

    var service = new KafkaEventPublishingService(HEADERS, vertxContext, kafkaService);
    var publishFuture = service.publishEvent(ITEM_CHECKED_IN.name(), PAYLOAD);

    var error = assertThrows(CompletionException.class, publishFuture::join);

    assertThat(error.getCause(), instanceOf(IllegalStateException.class));
  }
}
