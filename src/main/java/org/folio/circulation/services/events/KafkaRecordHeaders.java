package org.folio.circulation.services.events;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.util.HashMap;
import java.util.Map;

import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.experimental.UtilityClass;

@UtilityClass
class KafkaRecordHeaders {
  private static final String TENANT_ID_HEADER = "folio.tenantId";

  static Map<String, String> okapiHeaders(KafkaConsumerRecord<String, String> record,
    String defaultOkapiUrl) {

    Map<String, String> headers = new HashMap<>();
    record.headers().forEach(header -> headers.put(header.key(), header.value().toString()));

    putIfAbsentIgnoringCase(headers, OKAPI_URL, defaultOkapiUrl);
    putIfAbsentIgnoringCase(headers, TENANT, headers.get(TENANT_ID_HEADER));

    return headers;
  }

  private static void putIfAbsentIgnoringCase(Map<String, String> headers, String key,
    String value) {

    if (value != null && headers.keySet().stream().noneMatch(key::equalsIgnoreCase)) {
      headers.put(key, value);
    }
  }
}
