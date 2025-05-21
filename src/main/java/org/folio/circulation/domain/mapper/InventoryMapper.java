package org.folio.circulation.domain.mapper;

import static java.util.stream.Collectors.joining;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Publication;

public class InventoryMapper {

  private static final Logger log = LogManager.getLogger(InventoryMapper.class);

  private InventoryMapper() {
  }

  public static JsonObject createItemContext(Item item) {
    log.debug("createItemContext:: parameters item: {}", item);
    String yearCaptionsToken = String.join("; ", item.getYearCaption());
    String copyNumber = item.getCopyNumber() != null ? item.getCopyNumber() : "";
    String administrativeNotes = String.join("; ", item.getAdministrativeNotes());

    JsonObject itemContext = createInstanceContext(item.getInstance(), item)
      .put("barcode", item.getBarcode())
      .put("status", item.getStatus().getValue())
      .put("enumeration", item.getEnumeration())
      .put("volume", item.getVolume())
      .put("chronology", item.getChronology())
      .put("yearCaption", yearCaptionsToken)
      .put("materialType", item.getMaterialTypeName())
      .put("loanType", item.getLoanTypeName())
      .put("copy", copyNumber)
      .put("numberOfPieces", item.getNumberOfPieces())
      .put("displaySummary", item.getDisplaySummary())
      .put("descriptionOfPieces", item.getDescriptionOfPieces())
      .put("accessionNumber", item.getAccessionNumber())
      .put("administrativeNotes", administrativeNotes);

    var location = (item.canFloatThroughCheckInServicePoint() && item.isInStatus(ItemStatus.AVAILABLE)) ?
      item.getFloatDestinationLocation() : item.getLocation();

    if (location != null) {
      log.info("createItemContext:: location is not null");

      itemContext
        .put("effectiveLocationSpecific", location.getName())
        .put("effectiveLocationLibrary", location.getLibraryName())
        .put("effectiveLocationCampus", location.getCampusName())
        .put("effectiveLocationInstitution", item.isDcbItem() ? item.getLendingLibraryCode() : location.getInstitutionName())
        .put("effectiveLocationDiscoveryDisplayName", location.getDiscoveryDisplayName());

      var primaryServicePoint = location.getPrimaryServicePoint();
      if (primaryServicePoint != null) {
        log.info("createItemContext:: primaryServicePoint is not null");
        itemContext.put("effectiveLocationPrimaryServicePointName", primaryServicePoint.getName());
      }
    }

    CallNumberComponents callNumberComponents = item.getCallNumberComponents();
    if (callNumberComponents != null) {
      log.info("createItemContext:: callNumberComponents is not null");
      itemContext
        .put("callNumber", callNumberComponents.getCallNumber())
        .put("callNumberPrefix", callNumberComponents.getPrefix())
        .put("callNumberSuffix", callNumberComponents.getSuffix());
    }

    log.debug("createItemContext:: result {}", itemContext);
    return itemContext;
  }

  public static JsonObject createInstanceContext(Instance instance, Item item) {
    if (instance == null) {
      log.info("createInstanceContext:: instance is null");
      return new JsonObject();
    }

    return new JsonObject()
      .put("title", item != null && item.isDcbItem() ?
        item.getDcbItemTitle() : instance.getTitle())
      .put("instanceHrid", instance.getHrid())
      .put("primaryContributor", instance.getPrimaryContributorName())
      .put("allContributors", instance.getContributorNames().collect(joining("; ")))
      .put("datesOfPublication", instance.getPublication().stream().
        map(Publication::getDateOfPublication).collect(joining("; ")))
      .put("editions", String.join("; ", instance.getEditions()))
      .put("physicalDescriptions", String.join("; ", instance.getPhysicalDescriptions()));
  }
}
