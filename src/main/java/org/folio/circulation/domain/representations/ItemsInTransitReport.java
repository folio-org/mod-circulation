package org.folio.circulation.domain.representations;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.domain.representations.ContributorsToNamesMapper.mapContributorNamesToJson;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeNamedObject;

import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LastCheckIn;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.services.support.ItemsInTransitReportContext;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ItemsInTransitReport {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ItemsInTransitReportContext reportContext;

  public JsonObject build() {
    log.debug("build:: ");
    List<JsonObject> reportEntries = reportContext.getItems().values().stream()
      .sorted(sortByCheckinServicePointComparator())
      .map(this::buildEntry)
      .collect(toList());

    JsonObject result = new JsonObject()
      .put("items", new JsonArray(reportEntries))
      .put("totalRecords", reportEntries.size());
    log.info("build:: result {}", result);
    return result;
  }

  private Comparator<Item> sortByCheckinServicePointComparator() {
    log.debug("sortByCheckinServicePointComparator:: ");
    return comparing(item -> ofNullable(reportContext.getLoans().get(item.getItemId()))
      .map(Loan::getCheckInServicePointId)
      .map(id -> reportContext.getServicePoints().get(id))
      .map(ServicePoint::getName)
      .orElse(null), Comparator.nullsLast(String::compareTo));
  }

  private JsonObject buildEntry(Item item) {
    log.debug("buildEntry:: parameters item: {}", item);

    if (item == null || item.isNotFound()) {
      log.info("buildEntry:: item is null or not found");
      return new JsonObject();
    }

    item = ofNullable(item.getHoldingsRecordId())
      .map(reportContext.getHoldingsRecords()::get)
      .map(Holdings::getInstanceId)
      .map(reportContext.getInstances()::get)
      .map(item::withInstance)
      .orElse(item);

    Loan loan = reportContext.getLoans().get(item.getItemId());
    Request request = reportContext.getRequests().get(item.getItemId());

    Location location = null;
    if (item.getEffectiveLocationId() != null) {
      log.info("buildEntry:: effectiveLocationId is not null");
      location = reportContext.getLocations().get(item.getEffectiveLocationId());
    }
    if (location != null) {
      log.info("buildEntry:: location is not null");
      item = ofNullable(location.getPrimaryServicePointId())
        .map(UUID::toString)
        .map(reportContext.getServicePoints()::get)
        .map(location::withPrimaryServicePoint)
        .map(item::withLocation)
        .orElse(item);
    }

    item = ofNullable(item.getInTransitDestinationServicePointId())
      .map(reportContext.getServicePoints()::get)
      .map(item::updateDestinationServicePoint)
      .orElse(item);

    item = ofNullable(item.getLastCheckInServicePointId())
      .map(UUID::toString)
      .map(reportContext.getServicePoints()::get)
      .map(item::updateLastCheckInServicePoint)
      .orElse(item);

    final JsonObject entry = new JsonObject();

    write(entry, "id", item.getItemId());
    write(entry, "title", item.getTitle());
    write(entry, "barcode", item.getBarcode());
    write(entry, "contributors", mapContributorNamesToJson(item));
    write(entry, "callNumber", item.getCallNumber());
    write(entry, "enumeration", item.getEnumeration());
    write(entry, "displaySummary", item.getDisplaySummary());
    write(entry, "volume", item.getVolume());
    write(entry, "yearCaption", item.getYearCaption());
    writeNamedObject(entry, "status", ofNullable(item.getStatus())
      .map(ItemStatus::getValue).orElse(null));
    write(entry, "inTransitDestinationServicePointId",
      item.getInTransitDestinationServicePointId());
    write(entry, "copyNumber", item.getCopyNumber());
    write(entry, "effectiveCallNumberComponents",
      createCallNumberComponents(item.getCallNumberComponents()));

    ServicePoint inTransitDestinationServicePoint = item.getInTransitDestinationServicePoint();
    if (inTransitDestinationServicePoint != null) {
      log.info("buildEntry:: inTransitDestinationServicePoint is not null");
      writeServicePoint(entry, inTransitDestinationServicePoint, "inTransitDestinationServicePoint");
    }

    if (location != null) {
      log.info("buildEntry:: location is not null");
      writeLocation(entry, location);
    }

    if (request != null) {
      log.info("buildEntry:: request is not null");
      User requester = reportContext.getUsers().get(request.getRequesterId());

      PatronGroup requesterPatronGroup = requester == null ? null :
        reportContext.getPatronGroups().get(requester.getPatronGroupId());
      if (requesterPatronGroup != null) {
        request = request.withRequester(requester.withPatronGroup(requesterPatronGroup));
      }

      var pickupServicePoint = getServicePoint(request.getPickupServicePointId());
      log.info("buildEntry:: pickupServicePointId: {}", pickupServicePoint);
      request = request.withPickupServicePoint(pickupServicePoint);

      writeRequest(request, entry);
    }

    if (loan != null) {
      log.info("buildEntry:: loan is not null");
      var checkoutServicePoint = getServicePoint(loan.getCheckoutServicePointId());
      var checkInServicePoint = getServicePoint(loan.getCheckInServicePointId());

      loan = loan
        .withCheckinServicePoint(checkInServicePoint)
        .withCheckoutServicePoint(checkoutServicePoint);

      writeLoan(entry, loan);
    }

    final LastCheckIn lastCheckIn = item.getLastCheckIn();
    if (lastCheckIn != null) {
      log.info("buildEntry:: lastCheckIn is not null");
      writeLastCheckIn(entry, lastCheckIn);
    }

    log.info("buildEntry:: result {}", entry);
    return entry;
  }

  private ServicePoint getServicePoint(String servicePointId) {
    return ofNullable(servicePointId)
      .map(reportContext.getServicePoints()::get)
      .orElse(null);
  }

  private void writeLastCheckIn(JsonObject itemReport, LastCheckIn lastCheckIn) {
    log.debug("writeLastCheckIn:: parameters itemReport, lastCheckIn: {}", lastCheckIn);

    final JsonObject lastCheckInJson = new JsonObject();
    write(lastCheckInJson, "dateTime", lastCheckIn.getDateTime());
    final ServicePoint lastCheckInServicePoint = lastCheckIn.getServicePoint();
    if (lastCheckInServicePoint != null) {
      writeServicePoint(lastCheckInJson, lastCheckInServicePoint, "servicePoint");
    }
    write(itemReport, "lastCheckIn", lastCheckInJson);
  }

  private void writeLocation(JsonObject itemReport, Location location) {
    log.debug("writeLocation:: parameters itemReport, location: {}", location);

    final JsonObject locationJson = new JsonObject();
    write(locationJson, "name", location.getName());
    write(locationJson, "code", location.getCode());
    write(locationJson, "libraryName", location.getLibraryName());
    write(itemReport, "location", locationJson);
  }

  private void writeServicePoint(JsonObject jsonObject, ServicePoint servicePoint,
    String propertyName) {

    log.debug("writeServicePoint:: parameters jsonObject, servicePoint: {}, propertyName: {}",
      servicePoint, propertyName);

    final JsonObject servicePointJson = new JsonObject();
    write(servicePointJson, "id", servicePoint.getId());
    write(servicePointJson, "name", servicePoint.getName());
    write(jsonObject, propertyName, servicePointJson);
  }

  private void writeRequest(Request request, JsonObject itemReport) {
    log.debug("writeRequest:: parameters request: {}, itemReport", request);

    final JsonObject requestJson = new JsonObject();
    write(requestJson, "requestType", request.getRequestType().value);
    write(requestJson, "requestDate", request.getRequestDate());
    write(requestJson, "requestExpirationDate", request.getRequestExpirationDate());
    write(requestJson, "requestPickupServicePointName",
      ofNullable(request.getPickupServicePoint())
        .map(ServicePoint::getName).orElse(null));

    final JsonObject tags = request.asJson().getJsonObject("tags");
    if (tags != null) {
      log.info("writeRequest:: tags is not null");
      final JsonArray tagsJson = tags.getJsonArray("tagList");
      write(requestJson, "tags", tagsJson);
    }

    ofNullable(request.getRequester())
      .map(User::getPatronGroup)
      .ifPresent(pg -> write(requestJson, "requestPatronGroup", pg.getDesc()));

    write(itemReport, "request", requestJson);
  }

  private void writeLoan(JsonObject itemReport, Loan loan) {
    log.debug("writeLoan:: parameters itemReport, loan: {}", loan);

    final JsonObject loanJson = new JsonObject();
    writeCheckInServicePoint(loanJson, loan.getCheckinServicePoint());
    write(loanJson, "checkInDateTime", loan.getReturnDate());
    write(itemReport, "loan", loanJson);
  }

  private void writeCheckInServicePoint(JsonObject loanJson, ServicePoint servicePoint) {
    log.debug("writeCheckInServicePoint:: parameters loanJson: {}, servicePoint: {}", loanJson,
      servicePoint);

    if (servicePoint != null) {
      log.info("writeCheckInServicePoint:: servicePoint is not null");
      final JsonObject checkInServicePointJson = new JsonObject();
      write(checkInServicePointJson, "name", servicePoint.getName());
      write(checkInServicePointJson, "code", servicePoint.getCode());
      write(checkInServicePointJson, "discoveryDisplayName", servicePoint.getDiscoveryDisplayName());
      write(checkInServicePointJson, "description", servicePoint.getDescription());
      write(checkInServicePointJson, "shelvingLagTime", servicePoint.getShelvingLagTime());
      write(checkInServicePointJson, "pickupLocation", servicePoint.isPickupLocation());
      write(loanJson, "checkInServicePoint", checkInServicePointJson);
    }
  }
}
