package org.folio.circulation.domain.mapper;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.utils.ClockUtil;

import java.util.Objects;

import static org.folio.circulation.domain.mapper.UserMapper.createUserContext;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

public class StaffSlipMapper {
  private static final Logger log = LogManager.getLogger(StaffSlipMapper.class);

  private static final String ITEM = "item";
  private static final String REQUEST = "request";
  private static final String REQUESTER = "requester";
  private static final String CURRENT_DATE_TIME = "currentDateTime";

  private StaffSlipMapper() {
  }

  public static JsonObject createStaffSlipContext(Request request) {
    if (request == null) {
      return new JsonObject();
    }
    return createStaffSlipContext(request, request.getItem());
  }

  public static JsonObject createStaffSlipContext(Request request, Item item) {
    JsonObject staffSlipContext = new JsonObject();
    writeItem(staffSlipContext, item);
    writeRequest(staffSlipContext, request);
    write(staffSlipContext, CURRENT_DATE_TIME, ClockUtil.getZonedDateTime());
    return staffSlipContext;
  }

  private static void writeItem(JsonObject staffSlipContext, Item item) {
    if (item == null) {
      log.info("writeItem:: item is null");
      return;
    }
    JsonObject itemContext = InventoryMapper.createItemContext(item);
    if (item.getLastCheckIn() != null) {
      write(itemContext, "lastCheckedInDateTime", item.getLastCheckIn().getDateTime());
    }
    staffSlipContext.put(ITEM, itemContext);
  }

  private static void writeRequest(JsonObject staffSlipContext, Request request) {
    if (request == null) {
      log.info("writeRequest:: request is null");
      return;
    }
    staffSlipContext.put(REQUEST, RequestMapper.createRequestContext(request));

    User requester = request.getRequester();
    if (requester != null) {
      staffSlipContext.put(REQUESTER, createUserContext(requester, request.getDeliveryAddressTypeId()));
    }
  }

  public static JsonObject addPrimaryServicePointNameToStaffSlipContext(JsonObject entries,
    ServicePoint primaryServicePoint, String slipsCollectionName) {

    log.debug("addPrimaryServicePointNameToStaffSlipContext:: parameters " +
      "entries: {}, primaryServicePoint: {}, slipsCollectionName: {}",
      entries, primaryServicePoint, slipsCollectionName);
    if (primaryServicePoint == null) {
      log.info("addPrimaryServicePointNameToStaffSlipContext:: primaryServicePoint object is null");
      return entries;
    }

    if (entries == null) {
      log.info("addPrimaryServicePointNameToStaffSlipContext:: entries JsonObject is null, " +
        "primaryServicePointName: {}", primaryServicePoint.getName());
      return new JsonObject();
    }

    entries.getJsonArray(slipsCollectionName)
      .stream()
      .map(JsonObject.class::cast)
      .map(pickSlip -> pickSlip.getJsonObject(ITEM))
      .filter(Objects::nonNull)
      .forEach(item -> item.put("effectiveLocationPrimaryServicePointName", primaryServicePoint.getName()));

    log.debug("addPrimaryServicePointNameToStaffSlipContext:: Result entries: {}, " +
      "primaryServicePointName: {}", () -> entries, primaryServicePoint::getName);

    return entries;
  }
}
