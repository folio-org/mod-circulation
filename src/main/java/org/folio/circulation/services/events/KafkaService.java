package org.folio.circulation.services.events;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.kafka.services.KafkaTopic;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaService {

  private final KafkaAdminClientService kafkaAdminClientService;

  public KafkaService(Vertx vertx) {
    this.kafkaAdminClientService = new KafkaAdminClientService(vertx);
  }

  public CompletableFuture<Void> createCirculationTopics(String tenantId) {
    return createTopics(CirculationKafkaTopic.values(), tenantId);
  }

  public CompletableFuture<Void> createTopics(KafkaTopic[] topics, String tenantId) {
    return kafkaAdminClientService.createKafkaTopics(topics, tenantId)
      .toCompletionStage()
      .toCompletableFuture();
  }

  public CompletableFuture<Void> deleteCirculationTopics(String tenantId) {
    return deleteTopics(CirculationKafkaTopic.values(), tenantId);
  }

  public CompletableFuture<Void> deleteTopics(KafkaTopic[] topics, String tenantId) {
    return kafkaAdminClientService.deleteKafkaTopics(topics, tenantId)
      .toCompletionStage()
      .toCompletableFuture();
  }

  public KafkaEventPublisher<String, JsonObject> createPublisher(CirculationKafkaTopic topic,
    Context context, String tenantId) {

    return new KafkaEventPublisher<>(context, topic.fullTopicName(tenantId));
  }
}
