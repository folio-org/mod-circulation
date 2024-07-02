package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

@AllArgsConstructor(access = PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
public class PrintEventRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String REQUEST_IDS_FIELD = "requestIds";
  public static final String REQUESTER_ID_FIELD = "requesterId";
  public static final String REQUESTER_NAME_FIELD = "requesterName";
  public static final String PRINT_DATE_FIELD = "printEventDate";

  @ToString.Include
  @Getter
  private final JsonObject representation;

  @Getter
  private final List<String> requestIds;
  @Getter
  private final String requesterId;
  @Getter
  private final String requesterName;
  @Getter
  private final String printEventDate;

  public static PrintEventRequest from(JsonObject representation) {
    final var requestIds = getArrayProperty(representation, REQUEST_IDS_FIELD).stream()
      .map(String.class::cast)
      .toList();
    final var requesterId = getProperty(representation, REQUESTER_ID_FIELD);
    final var requesterName = getProperty(representation, REQUESTER_NAME_FIELD);
    final var printEventDate = getProperty(representation, PRINT_DATE_FIELD);

    if (requestIds.isEmpty() || null == requesterId || null == requesterName || null == printEventDate || !containsOnlyKnownFields(representation)) {
      log.info("from:: Print Event Request JSON is invalid: {},{},{},{},{}", representation, requestIds, requesterName, requesterId, printEventDate);
      return null;
    }
    return new PrintEventRequest(representation, requestIds, requesterId, requesterName, printEventDate);
  }

  private static boolean containsOnlyKnownFields(JsonObject representation) {
    return Set.of(REQUEST_IDS_FIELD, REQUESTER_ID_FIELD, REQUESTER_NAME_FIELD, PRINT_DATE_FIELD)
      .containsAll(representation.fieldNames());
  }
}
