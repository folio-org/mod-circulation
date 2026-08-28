package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

public interface EventPublishingService {
  CompletableFuture<Boolean> publishEvent(String eventType, String payload);
}
