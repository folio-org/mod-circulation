package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonPropertyWriter.writeNamedObject;

import java.util.Optional;

import org.folio.circulation.domain.InTransitReportEntry;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LastCheckIn;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ItemReportRepresentation {

  public JsonObject createItemReport(InTransitReportEntry inTransitReportEntry) {
    if (inTransitReportEntry == null) {
      return new JsonObject();
    }
    final Item item = inTransitReportEntry.getItem();

    if (item == null || item.isNotFound()) {
      return new JsonObject();
    }
    final JsonObject itemReport = new JsonObject();

    write(itemReport, "id", item.getItemId());
    write(itemReport, "title", item.getTitle());
    write(itemReport, "barcode", item.getBarcode());
    write(itemReport, "contributors", item.getContributorNames());
    write(itemReport, "callNumber", item.getCallNumber());
    write(itemReport, "enumeration", item.getEnumeration());
    write(itemReport, "volume", item.getVolume());
    write(itemReport, "yearCaption", new JsonArray(item.getYearCaption()));
    writeNamedObject(itemReport, "status", Optional.ofNullable(item.getStatus())
      .map(ItemStatus::getValue).orElse(null));
    write(itemReport, "inTransitDestinationServicePointId", item.getInTransitDestinationServicePointId());
    write(itemReport, "copyNumber", item.getCopyNumber());
    write(itemReport, "effectiveCallNumberComponents",
      createCallNumberComponents(item.getCallNumberComponents()));

    final ServicePoint inTransitDestinationServicePoint = item.getInTransitDestinationServicePoint();
    if (inTransitDestinationServicePoint != null) {
      writeServicePoint(itemReport, inTransitDestinationServicePoint, "inTransitDestinationServicePoint");
    }

    final Location location = item.getLocation();
    if (location != null) {
      writeLocation(itemReport, location);
    }
    final Request request = inTransitReportEntry.getRequest();
    if (request != null) {
      writeRequest(inTransitReportEntry.getRequest(), itemReport);
    }
    final Loan loan = inTransitReportEntry.getLoan();
    if (loan != null) {
      writeLoan(itemReport, loan);
    }

    final LastCheckIn lastCheckIn = item.getLastCheckIn();
    if (lastCheckIn != null) {
      writeLastCheckIn(itemReport, lastCheckIn);
    }

    return itemReport;
  }

  private void writeLastCheckIn(JsonObject itemReport, LastCheckIn lastCheckIn) {
    final JsonObject lastCheckInJson = new JsonObject();
    write(lastCheckInJson, "dateTime", lastCheckIn.getDateTime());
    final ServicePoint lastCheckInServicePoint = lastCheckIn.getServicePoint();
    if (lastCheckInServicePoint != null) {
      writeServicePoint(lastCheckInJson, lastCheckInServicePoint, "servicePoint");
    }
    write(itemReport, "lastCheckIn", lastCheckInJson);
  }

  private void writeLocation(JsonObject itemReport, Location location) {
    final JsonObject locationJson = new JsonObject();
    write(locationJson, "name", location.getName());
    write(locationJson, "code", location.getCode());
    write(locationJson, "libraryName", location.getLibraryName());
    write(itemReport, "location", locationJson);
  }

  private void writeServicePoint(JsonObject jsonObject,
                                 ServicePoint servicePoint,
                                 String propertyName) {
    final JsonObject servicePointJson = new JsonObject();
    write(servicePointJson, "id", servicePoint.getId());
    write(servicePointJson, "name", servicePoint.getName());
    write(jsonObject, propertyName, servicePointJson);
  }

  private void writeRequest(Request request, JsonObject itemReport) {
    final JsonObject requestJson = new JsonObject();
    write(requestJson, "requestType", request.getRequestType().value);
    write(requestJson, "requestDate", request.getRequestDate());
    write(requestJson, "requestExpirationDate", request.getRequestExpirationDate());
    write(requestJson, "requestPickupServicePointName",
      Optional.ofNullable(request.getPickupServicePoint())
        .map(ServicePoint::getName).orElse(null));

    final JsonObject tags = request.asJson().getJsonObject("tags");
    if (tags != null) {
      final JsonArray tagsJson = tags.getJsonArray("tagList");
      write(requestJson, "tags", tagsJson);
    }

    PatronGroup patronGroup = Optional.ofNullable(request.getRequester())
      .map(User::getPatronGroup).orElse(null);
    if (patronGroup != null){
      write(requestJson, "requestPatronGroup", patronGroup.getDesc());
    }
    write(itemReport, "request", requestJson);

  }

  private void writeLoan(JsonObject itemReport, Loan loan) {
    final JsonObject loanJson = new JsonObject();
    writeCheckInServicePoint(loanJson, loan.getCheckinServicePoint());
    write(loanJson, "checkInDateTime", loan.getReturnDate());
    write(itemReport, "loan", loanJson);
  }

  private void writeCheckInServicePoint(JsonObject loanJson, ServicePoint servicePoint) {
    final JsonObject checkInServicePointJson = new JsonObject();
    write(checkInServicePointJson, "name", servicePoint.getName());
    write(checkInServicePointJson, "code", servicePoint.getCode());
    write(checkInServicePointJson, "discoveryDisplayName", servicePoint.getDiscoveryDisplayName());
    write(checkInServicePointJson, "description", servicePoint.getDescription());
    write(checkInServicePointJson, "shelvingLagTime", servicePoint.getShelvingLagTime());
    write(checkInServicePointJson, "pickupLocation", servicePoint.getPickupLocation());
    write(loanJson, "checkInServicePoint", checkInServicePointJson);
  }

}
