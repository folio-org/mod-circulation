package org.folio.circulation.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class RequestRules {

  private static EnumMap<ItemStatus, Boolean> recallRules;
  private static EnumMap<ItemStatus, Boolean> holdRules;
  private static EnumMap<ItemStatus, Boolean> pageRules;

  static{
    initRecallRules();
    initHoldRules();
    initPageRules();
  }

  private RequestRules(){
    throw new IllegalStateException();
  }

  private static void initRecallRules(){
    recallRules = new EnumMap<>(ItemStatus.class);
    recallRules.put(ItemStatus.CHECKED_OUT, true);
    recallRules.put(ItemStatus.AVAILABLE, false);
    recallRules.put(ItemStatus.AWAITING_PICKUP, true);
    recallRules.put(ItemStatus.IN_TRANSIT, true);
    recallRules.put(ItemStatus.MISSING, false);
    recallRules.put(ItemStatus.PAGED, false);
    recallRules.put(ItemStatus.NONE, false);
  }

  private static void initHoldRules(){
    holdRules = new EnumMap<>(ItemStatus.class);
    holdRules.put(ItemStatus.CHECKED_OUT, true);
    holdRules.put(ItemStatus.AVAILABLE, false);
    holdRules.put(ItemStatus.AWAITING_PICKUP, true);
    holdRules.put(ItemStatus.IN_TRANSIT, true);
    holdRules.put(ItemStatus.MISSING, true);
    holdRules.put(ItemStatus.PAGED, true);
    holdRules.put(ItemStatus.NONE, true);
  }

  private static void initPageRules(){
    pageRules = new EnumMap<>(ItemStatus.class);
    pageRules.put(ItemStatus.CHECKED_OUT, false);
    pageRules.put(ItemStatus.AVAILABLE, true);
    pageRules.put(ItemStatus.AWAITING_PICKUP, false);
    pageRules.put(ItemStatus.IN_TRANSIT, false);
    pageRules.put(ItemStatus.MISSING, false);
    pageRules.put(ItemStatus.PAGED, false);
    pageRules.put(ItemStatus.NONE, false);
  }

  public static boolean canCreateRequestForItem(ItemStatus itemStatus, RequestType requestType){
    if (requestType == RequestType.HOLD){
      return holdRules.get(itemStatus);
    } else if (requestType == RequestType.RECALL){
      return recallRules.get(itemStatus);
    } else if (requestType == RequestType.PAGE){
      return pageRules.get(itemStatus);
    } else {
      return false;
    }
  }
}
