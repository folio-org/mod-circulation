package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.domain.representations.ContributorsToNamesMapper.mapContributorNamesToJson;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class ItemSummaryRepresentation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject createItemSummary(Item item) {
    log.debug("createItemSummary:: parameters item: {}", item);

    if (item == null || item.isNotFound()) {
      log.info("createItemSummary:: item is null or not found");
      return new JsonObject();
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "id", item.getItemId());
    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());
    write(itemSummary, "title", item.isDcbItem() ? item.getDcbItemTitle() : item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());
    write(itemSummary, "contributors", mapContributorNamesToJson(item));
    write(itemSummary, "primaryContributor",item.getPrimaryContributorName());
    write(itemSummary, "callNumber", item.getCallNumber());
    write(itemSummary, "enumeration", item.getEnumeration());
    write(itemSummary, "chronology", item.getChronology());
    write(itemSummary, "displaySummary", item.getDisplaySummary());
    write(itemSummary, "volume", item.getVolume());
    write(itemSummary, "copyNumber", item.getCopyNumber());
    write(itemSummary, CALL_NUMBER_COMPONENTS,
      createCallNumberComponents(item.getCallNumberComponents()));
    write(itemSummary, "tenantId", item.getTenantId());

    JsonObject status = new JsonObject()
      .put("name", item.getStatus().getValue());

    if (Objects.nonNull(item.getStatus().getDate())){
      log.info("createItemSummary:: item.getStatus().getDate() is not null");
      status.put("date", item.getStatus().getDate());
    }

    write(itemSummary, ItemProperties.STATUS_PROPERTY, status);

    write(itemSummary, "inTransitDestinationServicePointId",
      item.getInTransitDestinationServicePointId());

    final ServicePoint inTransitDestinationServicePoint
      = item.getInTransitDestinationServicePoint();

    if (inTransitDestinationServicePoint != null) {
      log.info("createItemSummary:: inTransitDestinationServicePoint is not null");
      final JsonObject destinationServicePointSummary = new JsonObject();

      write(destinationServicePointSummary, "id",
        inTransitDestinationServicePoint.getId());

      write(destinationServicePointSummary, "name",
        inTransitDestinationServicePoint.getName());

      write(itemSummary, "inTransitDestinationServicePoint",
        destinationServicePointSummary);
    }

    final Location location = item.getLocation();

    if (item.canFloatThroughCheckInServicePoint() && item.isInStatus(ItemStatus.AVAILABLE)) {
      itemSummary.put("location", new JsonObject()
        .put("name", item.getFloatDestinationLocation().getName()));
    } else if (location != null) {
      log.info("createItemSummary:: location is not null");
      itemSummary.put("location", new JsonObject()
        .put("name", location.getName()));
    }

    writeByPath(itemSummary, item.getMaterialTypeName(), "materialType", "name");

    log.info("createItemSummary:: result {}", itemSummary);
    return itemSummary;
  }
}
