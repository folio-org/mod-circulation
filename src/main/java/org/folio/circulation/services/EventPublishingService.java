package org.folio.circulation.services;

import java.util.concurrent.CompletableFuture;

public interface EventPublishingService {
  CompletableFuture<Void> publishEvent(String eventType, String payload);
}
