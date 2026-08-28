package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_IN;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletionException;

import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.services.events.KafkaEventPublisher;
import org.folio.circulation.services.events.KafkaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Context;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublishingServiceTest {
  private static final String TENANT_ID = "test_tenant";
  private static final String PAYLOAD = "{}";
  private static final Map<String, String> OKAPI_HEADERS = Map.of(TENANT, TENANT_ID);

  @Mock
  private Context vertxContext;
  @Mock
  private KafkaService kafkaService;
  @Mock
  private KafkaEventPublisher<String> publisher;

  @Test
  void failsWhenKafkaPublisherFails() {
    var failure = new IllegalStateException("Kafka publish failed");
    when(kafkaService.createPublisher(CirculationKafkaTopic.ITEM_CHECKED_IN, vertxContext,
      TENANT_ID)).thenReturn(publisher);
    when(publisher.publish(anyString(), eq(PAYLOAD), eq(OKAPI_HEADERS)))
      .thenReturn(failedFuture(failure));

    var service = new KafkaEventPublishingService(OKAPI_HEADERS, vertxContext, kafkaService);

    var error = assertThrows(CompletionException.class,
      () -> service.publishEvent(ITEM_CHECKED_IN.name(), PAYLOAD).join());

    assertThat(error.getCause(), instanceOf(IllegalStateException.class));
    assertThat(error.getCause(), sameInstance(failure));
  }
}
