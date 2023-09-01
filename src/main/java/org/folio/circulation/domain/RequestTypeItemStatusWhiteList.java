package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_DELIVERY;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.INTELLECTUAL_ITEM;
import static org.folio.circulation.domain.ItemStatus.IN_PROCESS;
import static org.folio.circulation.domain.ItemStatus.IN_PROCESS_NON_REQUESTABLE;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatus.LONG_MISSING;
import static org.folio.circulation.domain.ItemStatus.LOST_AND_PAID;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.domain.ItemStatus.NONE;
import static org.folio.circulation.domain.ItemStatus.ON_ORDER;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.ItemStatus.RESTRICTED;
import static org.folio.circulation.domain.ItemStatus.UNAVAILABLE;
import static org.folio.circulation.domain.ItemStatus.UNKNOWN;
import static org.folio.circulation.domain.ItemStatus.WITHDRAWN;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

public class RequestTypeItemStatusWhiteList {
  private static EnumMap<ItemStatus, Boolean> recallRules;
  private static EnumMap<ItemStatus, Boolean> holdRules;
  private static EnumMap<ItemStatus, Boolean> pageRules;
  private static EnumMap<ItemStatus, Boolean> noneRules;
  private static EnumMap<RequestType, EnumMap<ItemStatus, Boolean>> requestsRulesMap;

  static {
    initRecallRules();
    initHoldRules();
    initPageRules();
    initNoneRules();
    initRequestRulesMap();
  }

  private RequestTypeItemStatusWhiteList() {
    throw new IllegalStateException();
  }

  private static void initRecallRules() {
    recallRules = new EnumMap<>(ItemStatus.class);
    recallRules.put(CHECKED_OUT, true);
    recallRules.put(AVAILABLE, false);
    recallRules.put(AWAITING_PICKUP, true);
    recallRules.put(AWAITING_DELIVERY, true);
    recallRules.put(IN_TRANSIT, true);
    recallRules.put(MISSING, false);
    recallRules.put(PAGED, true);
    recallRules.put(ON_ORDER, true);
    recallRules.put(IN_PROCESS, true);
    recallRules.put(RESTRICTED, true);
    recallRules.put(DECLARED_LOST, false);
    recallRules.put(CLAIMED_RETURNED, false);
    recallRules.put(WITHDRAWN, false);
    recallRules.put(LOST_AND_PAID, false);
    recallRules.put(AGED_TO_LOST, false);
    recallRules.put(NONE, false);
    recallRules.put(INTELLECTUAL_ITEM, false);
    recallRules.put(IN_PROCESS_NON_REQUESTABLE, false);
    recallRules.put(LONG_MISSING, false);
    recallRules.put(UNAVAILABLE, false);
    recallRules.put(UNKNOWN, false);
  }

  private static void initHoldRules() {
    holdRules = new EnumMap<>(ItemStatus.class);
    holdRules.put(CHECKED_OUT, true);
    holdRules.put(AVAILABLE, false);
    holdRules.put(AWAITING_PICKUP, true);
    holdRules.put(AWAITING_DELIVERY, true);
    holdRules.put(IN_TRANSIT, true);
    holdRules.put(MISSING, true);
    holdRules.put(PAGED, true);
    holdRules.put(ON_ORDER, true);
    holdRules.put(IN_PROCESS, true);
    holdRules.put(RESTRICTED, true);
    holdRules.put(DECLARED_LOST, false);
    holdRules.put(CLAIMED_RETURNED, false);
    holdRules.put(WITHDRAWN, false);
    holdRules.put(LOST_AND_PAID, false);
    holdRules.put(AGED_TO_LOST, false);
    holdRules.put(NONE, true);
    holdRules.put(INTELLECTUAL_ITEM, false);
    holdRules.put(IN_PROCESS_NON_REQUESTABLE, false);
    holdRules.put(LONG_MISSING, false);
    holdRules.put(UNAVAILABLE, false);
    holdRules.put(UNKNOWN, false);
  }

  private static void initPageRules() {
    pageRules = new EnumMap<>(ItemStatus.class);
    pageRules.put(CHECKED_OUT, false);
    pageRules.put(AVAILABLE, true);
    pageRules.put(RESTRICTED, true);
    pageRules.put(AWAITING_PICKUP, false);
    pageRules.put(AWAITING_DELIVERY, false);
    pageRules.put(IN_TRANSIT, false);
    pageRules.put(MISSING, false);
    pageRules.put(PAGED, false);
    pageRules.put(ON_ORDER, false);
    pageRules.put(IN_PROCESS, false);
    pageRules.put(DECLARED_LOST, false);
    pageRules.put(CLAIMED_RETURNED, false);
    pageRules.put(WITHDRAWN, false);
    pageRules.put(LOST_AND_PAID, false);
    pageRules.put(AGED_TO_LOST, false);
    pageRules.put(NONE, false);
    pageRules.put(INTELLECTUAL_ITEM, false);
    pageRules.put(IN_PROCESS_NON_REQUESTABLE, false);
    pageRules.put(LONG_MISSING, false);
    pageRules.put(UNAVAILABLE, false);
    pageRules.put(UNKNOWN, false);
  }

  private static void initNoneRules() {
    noneRules = new EnumMap<>(ItemStatus.class);
    noneRules.put(CHECKED_OUT, false);
    noneRules.put(AVAILABLE, false);
    noneRules.put(AWAITING_PICKUP, false);
    noneRules.put(AWAITING_DELIVERY, false);
    noneRules.put(IN_TRANSIT, false);
    noneRules.put(MISSING, false);
    noneRules.put(PAGED, false);
    noneRules.put(ON_ORDER, false);
    noneRules.put(IN_PROCESS, false);
    noneRules.put(DECLARED_LOST, false);
    noneRules.put(CLAIMED_RETURNED, false);
    noneRules.put(WITHDRAWN, false);
    noneRules.put(LOST_AND_PAID, false);
    noneRules.put(AGED_TO_LOST, false);
    noneRules.put(NONE, false);
    noneRules.put(INTELLECTUAL_ITEM, false);
    noneRules.put(IN_PROCESS_NON_REQUESTABLE, false);
    noneRules.put(LONG_MISSING, false);
    noneRules.put(UNAVAILABLE, false);
    noneRules.put(UNKNOWN, false);
  }

  private static void initRequestRulesMap() {
    requestsRulesMap = new EnumMap<>(RequestType.class);
    requestsRulesMap.put(RequestType.HOLD, holdRules);
    requestsRulesMap.put(RequestType.PAGE, pageRules);
    requestsRulesMap.put(RequestType.RECALL, recallRules);
    requestsRulesMap.put(RequestType.NONE, noneRules);
  }

  public static boolean canCreateRequestForItem(ItemStatus itemStatus, RequestType requestType) {
    return requestsRulesMap.get(requestType).get(itemStatus);
  }

  public static List<RequestType> getRequestTypesAllowedForItemStatus(ItemStatus itemStatus) {
    return Arrays.stream(RequestType.values())
      .filter(requestType -> requestsRulesMap.get(requestType).get(itemStatus))
      .toList();
  }
}
