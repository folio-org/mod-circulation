package api.support.fakes;

import static api.support.APITestContext.getTenantId;
import static api.support.fakes.Storage.getStorage;
import static api.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.folio.circulation.domain.representations.ItemProperties.EFFECTIVE_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.HOLDINGS_RECORD_ID;
import static org.folio.circulation.domain.representations.ItemProperties.PERMANENT_LOCATION_ID;
import static org.folio.circulation.domain.representations.ItemProperties.TEMPORARY_LOCATION_ID;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.circulation.domain.representations.ItemProperties;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public final class StorageRecordPreProcessors {
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

  public static JsonObject setEffectiveLocationIdForItem(
    @SuppressWarnings("unused") JsonObject oldItem, JsonObject newItem) {

    final String holdingsRecordId = newItem.getString("holdingsRecordId");
    final JsonObject holding = getHoldingById(holdingsRecordId);

    newItem.put(EFFECTIVE_LOCATION_ID, firstNonNull(
      newItem.getString(TEMPORARY_LOCATION_ID),
      newItem.getString(PERMANENT_LOCATION_ID),
      holding.getString(TEMPORARY_LOCATION_ID),
      holding.getString(PERMANENT_LOCATION_ID)));

    return newItem;
  }

  public static JsonObject setItemStatusDateForItem(JsonObject oldItem, JsonObject newItem) {
    if (Objects.nonNull(oldItem)) {
      JsonObject oldItemStatus = oldItem.getJsonObject(ItemProperties.STATUS_PROPERTY);
      JsonObject newItemStatus = newItem.getJsonObject(ItemProperties.STATUS_PROPERTY);
      if (ObjectUtils.allNotNull(oldItemStatus, newItemStatus)) {
        if (!Objects.equals(oldItemStatus.getString("name"),
          newItemStatus.getString("name"))) {
          write(newItemStatus, "date",
            ClockUtil.getDateTime().toString(RMB_DATETIME_PATTERN)
          );
        }
      }
    }
    return newItem;
  }

  public static JsonObject setEffectiveCallNumberComponents(
    @SuppressWarnings("unused") JsonObject oldItem, JsonObject newItem) {

    final JsonObject effectiveCallNumberComponents = new JsonObject();
    final String holdingsId = newItem.getString(HOLDINGS_RECORD_ID);
    final JsonObject holding = getHoldingById(holdingsId);

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
    newItem.put("effectiveCallNumberComponents", effectiveCallNumberComponents);

    return newItem;
  }

  private static JsonObject getHoldingById(String id) {
    return getStorage()
      .getTenantResources(holdingsStorageUrl("").getPath(), getTenantId())
      .get(id);
  }
}
