package api.support.fakes.processors;

import static api.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.folio.circulation.domain.representations.ItemProperties.EFFECTIVE_CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.domain.representations.ItemProperties.HOLDINGS_RECORD_ID;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.support.APITestContext;
import io.vertx.core.json.JsonObject;

public final class StorageRecordPreProcessors {
  private static final Logger log = LoggerFactory.getLogger(StorageRecordPreProcessors.class);

  private static final List<String> CALL_NUMBER_PROPERTIES = Arrays.asList(
    "callNumber", "callNumberPrefix", "callNumberSuffix"
  );

  private StorageRecordPreProcessors() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  @SuppressWarnings("unused")
  public static CompletableFuture<JsonObject> setEffectiveLocationIdForItem(
    JsonObject oldItem, JsonObject newItem) {

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
          write(newItemStatus, "date", new DateTime());
        }
      }
    }
    return CompletableFuture.completedFuture(newItem);
  }

  @SuppressWarnings("unused")
  public static CompletableFuture<JsonObject> setEffectiveCallNumberComponents(
    JsonObject oldItem, JsonObject newItem) {

    CompletableFuture<JsonObject> holdings =
      CompletableFuture.completedFuture(new JsonObject());

    boolean hasItemLevelCallNumber = StringUtils
      .isNotBlank(newItem.getString("itemLevelCallNumber"));

    if (!hasItemLevelCallNumber) {
      String holdingsId = newItem.getString(HOLDINGS_RECORD_ID);
      holdings = getHoldingById(holdingsId);
    }

    return holdings.thenApply(holding -> {
      JsonObject effectiveCallNumberComponents = new JsonObject();

      CALL_NUMBER_PROPERTIES.forEach(callComponentName -> {
        final String itemLevelCallComponentPropertyName =
          getItemLevelCallNumberComponentName(callComponentName);

        final String propertyValue = hasItemLevelCallNumber
          ? newItem.getString(itemLevelCallComponentPropertyName)
          : holding.getString(callComponentName);

        effectiveCallNumberComponents.put(callComponentName, propertyValue);
      });

      return newItem.put(EFFECTIVE_CALL_NUMBER_COMPONENTS, effectiveCallNumberComponents);
    });
  }

  private static CompletableFuture<JsonObject> getHoldingById(String id) {
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    APITestContext
      .createClient(ex -> log.warn("Error: ", ex))
      .get(holdingsStorageUrl("?query=id=" + id), ResponseHandler.json(getCompleted));

    return getCompleted
      .thenApply(response -> response.getJson().getJsonArray("holdingsRecords")
        .getJsonObject(0)
      );
  }

  private static String getItemLevelCallNumberComponentName(String effectivePropertyName) {
    return "itemLevel" + StringUtils.capitalize(effectivePropertyName);
  }
}
