package org.folio.circulation.services.events;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import org.folio.circulation.domain.events.CirculationKafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KafkaEventPublisherFactory {

  public static KafkaEventPublisher<String, JsonObject> itemCheckedInEventPublisher(
    Context vertxContext, Map<String, String> okapiHeaders) {

    return new KafkaEventPublisher<>(vertxContext,
      CirculationKafkaTopic.ITEM_CHECKED_IN.fullTopicName(tenantId(okapiHeaders)));
  }
}
