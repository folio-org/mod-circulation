package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonPropertyWriter.writeNamedObject;

public class ItemReportRepresentation {

  public JsonObject createItemSummary(Item item) {
    if (item == null || item.isNotFound()) {
      return new JsonObject();
    }

    JsonObject itemReport = new JsonObject();

    write(itemReport, "id", item.getItemId());
    write(itemReport, "title", item.getTitle());
    write(itemReport, "barcode", item.getBarcode());
    write(itemReport, "contributors", item.getContributorNames());
    write(itemReport, "callNumber", item.getCallNumber());

    writeNamedObject(itemReport, "status", item.getStatus().getValue());

    write(itemReport, "inTransitDestinationServicePointId",
      item.getInTransitDestinationServicePointId());

    final ServicePoint inTransitDestinationServicePoint
      = item.getInTransitDestinationServicePoint();

    if (inTransitDestinationServicePoint != null) {
      final JsonObject destinationServicePointSummary = new JsonObject();

      write(destinationServicePointSummary, "id",
        inTransitDestinationServicePoint.getId());

      write(destinationServicePointSummary, "name",
        inTransitDestinationServicePoint.getName());

      write(itemReport, "inTransitDestinationServicePoint",
        destinationServicePointSummary);
    }

    final Location location = item.getLocation();

    if (location != null) {
      itemReport.put("location", new JsonObject()
        .put("name", location.getName())
        .put("code", location.getCode())
        .put("libraryName", location.getLibraryName()));
    }

    return itemReport;
  }
}
