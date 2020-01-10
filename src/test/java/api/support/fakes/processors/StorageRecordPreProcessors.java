package api.support.fakes.processors;

import static api.support.APITestContext.createWebClient;
import static api.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.folio.circulation.domain.representations.ItemProperties.HOLDINGS_RECORD_ID;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public final class StorageRecordPreProcessors {
  private static final Logger log = LoggerFactory.getLogger(StorageRecordPreProcessors.class);

  // Holdings record property name, item property name, effective property name
  private static final List<Triple<String, String, String>> CALL_NUMBER_PROPERTIES = Arrays.asList(
    new ImmutableTriple<>("callNumber", "itemLevelCallNumber", "callNumber"),
    new ImmutableTriple<>("callNumberPrefix", "itemLevelCallNumberPrefix", "prefix"),
    new ImmutableTriple<>("callNumberSuffix", "itemLevelCallNumberSuffix", "suffix")
  );
  // RMB uses ISO-8601 compatible date time format by default.
  private static final String RMB_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS+0000";

  private StorageRecordPreProcessors() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static CompletableFuture<JsonObject> setEffectiveLocationIdForItem(
    @SuppressWarnings("unused") JsonObject oldItem, JsonObject newItem) {

    String permanentLocationId = newItem.getString(ItemProperties.PERMANENT_LOCATION_ID);
    String temporaryLocationId = newItem.getString(ItemProperties.TEMPORARY_LOCATION_ID);

    if (ObjectUtils.anyNotNull(temporaryLocationId, permanentLocationId)) {
      newItem.put(
        ItemProperties.EFFECTIVE_LOCATION_ID,
        firstNonNull(temporaryLocationId, permanentLocationId)
      );

      return CompletableFuture.completedFuture(newItem);
    }

    final String holdingsRecordId = newItem.getString("holdingsRecordId");
    CompletableFuture<JsonObject> getCompleted = getHoldingById(holdingsRecordId);

    return getCompleted.thenApply(holding -> {
      String permanentLocation = holding.getString(ItemProperties.PERMANENT_LOCATION_ID);
      String temporaryLocation = holding.getString(ItemProperties.TEMPORARY_LOCATION_ID);

      return newItem.put(ItemProperties.EFFECTIVE_LOCATION_ID,
        firstNonNull(temporaryLocation, permanentLocation)
      );
    });
  }

  public static CompletableFuture<JsonObject> setItemStatusDateForItem(
    JsonObject oldItem, JsonObject newItem) {

    if (Objects.nonNull(oldItem)) {
      JsonObject oldItemStatus = oldItem.getJsonObject(ItemProperties.STATUS_PROPERTY);
      JsonObject newItemStatus = newItem.getJsonObject(ItemProperties.STATUS_PROPERTY);
      if (ObjectUtils.allNotNull(oldItemStatus, newItemStatus)) {
        if (!Objects.equals(oldItemStatus.getString("name"),
          newItemStatus.getString("name"))) {
          write(newItemStatus, "date",
            DateTime.now(DateTimeZone.UTC).toString(RMB_DATETIME_PATTERN)
          );
        }
      }
    }
    return CompletableFuture.completedFuture(newItem);
  }

  public static CompletableFuture<JsonObject> setEffectiveCallNumberComponents(
    @SuppressWarnings("unused") JsonObject oldItem, JsonObject newItem) {

    CompletableFuture<JsonObject> holdings =
      CompletableFuture.completedFuture(new JsonObject());

    boolean shouldRetrieveHoldings = CALL_NUMBER_PROPERTIES.stream()
      .map(Triple::getMiddle)
      .anyMatch(property -> StringUtils.isBlank(newItem.getString(property)));

    if (shouldRetrieveHoldings) {
      String holdingsId = newItem.getString(HOLDINGS_RECORD_ID);
      holdings = getHoldingById(holdingsId);
    }

    return holdings.thenApply(holding -> {
      JsonObject effectiveCallNumberComponents = new JsonObject();

      CALL_NUMBER_PROPERTIES.forEach(properties -> {
        String itemPropertyName = properties.getMiddle();
        String holdingsPropertyName = properties.getLeft();
        String effectivePropertyName = properties.getRight();

        final String propertyValue = StringUtils.firstNonBlank(
          newItem.getString(itemPropertyName),
          holding.getString(holdingsPropertyName)
        );

        if (StringUtils.isNotBlank(propertyValue)) {
          effectiveCallNumberComponents.put(effectivePropertyName, propertyValue);
        }
      });

      return newItem.put("effectiveCallNumberComponents",
        effectiveCallNumberComponents);
    });
  }

  private static CompletableFuture<JsonObject> getHoldingById(String id) {
    return createWebClient()
      .get(holdingsStorageUrl("?query=id=" + id))
      .thenApply(result -> result
        .map(StorageRecordPreProcessors::getFirstHoldingsRecord)
        .orElse(null));
  }

  private static JsonObject getFirstHoldingsRecord(Response response) {
    return response.getJson().getJsonArray("holdingsRecords").getJsonObject(0);
  }
}
