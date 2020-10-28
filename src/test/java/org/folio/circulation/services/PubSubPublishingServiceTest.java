package org.folio.circulation.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

@RunWith(MockitoJUnitRunner.class)
public class PubSubPublishingServiceTest {
  @Mock
  private RoutingContext routingContext;

  @Mock
  private HttpServerRequest request;

  private PubSubPublishingService pubSubPublishingService;
  private Throwable pubSubClientThrowable;

  @Before
  public void setUp() {
    when(routingContext.request()).thenReturn(request);
    when(request.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
    pubSubPublishingService = new PubSubPublishingService(routingContext);
  }

  @Test
  public void shouldFailWhenPubSubClientThrowsException() {
    PubSubPublishingService pubSubPublishingServiceSpy = spy(pubSubPublishingService);
    when(pubSubPublishingServiceSpy.getPubSubClient()).thenThrow(
      new IllegalArgumentException("Error message"));
    CompletableFuture<Boolean> future = pubSubPublishingServiceSpy.publishEvent(
      "EVENT_TYPE", "EVENT_PAYLOAD").whenComplete(
      (Boolean result, Throwable throwable) -> pubSubClientThrowable = throwable);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(future::isCompletedExceptionally);

    assertEquals("Error message", pubSubClientThrowable.getMessage());
  }
}
