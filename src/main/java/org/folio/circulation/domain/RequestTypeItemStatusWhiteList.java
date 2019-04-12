package org.folio.circulation.domain;

import java.util.EnumMap;

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
    recallRules.put(ItemStatus.CHECKED_OUT, true);
    recallRules.put(ItemStatus.AVAILABLE, false);
    recallRules.put(ItemStatus.AWAITING_PICKUP, true);
    recallRules.put(ItemStatus.IN_TRANSIT, true);
    recallRules.put(ItemStatus.MISSING, false);
    recallRules.put(ItemStatus.PAGED, false);
    recallRules.put(ItemStatus.ON_ORDER, true);
    recallRules.put(ItemStatus.IN_PROCESS, true);
    recallRules.put(ItemStatus.PAGED, false);
    recallRules.put(ItemStatus.NONE, false);
  }

  private static void initHoldRules() {
    holdRules = new EnumMap<>(ItemStatus.class);
    holdRules.put(ItemStatus.CHECKED_OUT, true);
    holdRules.put(ItemStatus.AVAILABLE, false);
    holdRules.put(ItemStatus.AWAITING_PICKUP, true);
    holdRules.put(ItemStatus.IN_TRANSIT, true);
    holdRules.put(ItemStatus.MISSING, true);
    holdRules.put(ItemStatus.PAGED, true);
    holdRules.put(ItemStatus.ON_ORDER, true);
    holdRules.put(ItemStatus.IN_PROCESS, true);
    holdRules.put(ItemStatus.NONE, true);
  }

  private static void initPageRules() {
    pageRules = new EnumMap<>(ItemStatus.class);
    pageRules.put(ItemStatus.CHECKED_OUT, false);
    pageRules.put(ItemStatus.AVAILABLE, true);
    pageRules.put(ItemStatus.AWAITING_PICKUP, false);
    pageRules.put(ItemStatus.IN_TRANSIT, false);
    pageRules.put(ItemStatus.MISSING, false);
    pageRules.put(ItemStatus.PAGED, false);
    pageRules.put(ItemStatus.ON_ORDER, false);
    pageRules.put(ItemStatus.IN_PROCESS, false);
    pageRules.put(ItemStatus.NONE, false);
  }

  private static void initNoneRules() {
    noneRules = new EnumMap<>(ItemStatus.class);
    noneRules.put(ItemStatus.CHECKED_OUT, false);
    noneRules.put(ItemStatus.AVAILABLE, false);
    noneRules.put(ItemStatus.AWAITING_PICKUP, false);
    noneRules.put(ItemStatus.IN_TRANSIT, false);
    noneRules.put(ItemStatus.MISSING, false);
    noneRules.put(ItemStatus.PAGED, false);
    noneRules.put(ItemStatus.ON_ORDER, false);
    noneRules.put(ItemStatus.IN_PROCESS, false);
    noneRules.put(ItemStatus.NONE, false);
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
}
