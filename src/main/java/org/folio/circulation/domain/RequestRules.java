package org.folio.circulation.domain;

import java.util.HashMap;
import java.util.Map;

public class RequestRules {

  private static Map<ItemStatus, Boolean> _recallRules;
  private static Map<ItemStatus, Boolean> _holdRules;
  private static Map<ItemStatus, Boolean> _pageRules;

  static{
    initRecallRules();
    initHoldRules();
    initPageRules();
  }

  private static void initRecallRules(){
    _recallRules = new HashMap();
    _recallRules.put(ItemStatus.CHECKED_OUT, true);
    _recallRules.put(ItemStatus.AVAILABLE, false);
    _recallRules.put(ItemStatus.AWAITING_PICKUP, true);
    _recallRules.put(ItemStatus.IN_TRANSIT, true);
    _recallRules.put(ItemStatus.MISSING, false);
    _recallRules.put(ItemStatus.PAGED, false);
    _recallRules.put(ItemStatus.NONE, false);
  }

  private static void initHoldRules(){
    _holdRules = new HashMap();
    _holdRules.put(ItemStatus.CHECKED_OUT, true);
    _holdRules.put(ItemStatus.AVAILABLE, false);
    _holdRules.put(ItemStatus.AWAITING_PICKUP, true);
    _holdRules.put(ItemStatus.IN_TRANSIT, true);
    _holdRules.put(ItemStatus.MISSING, true);
    _holdRules.put(ItemStatus.PAGED, true);
    _holdRules.put(ItemStatus.NONE, true);
  }

  private static void initPageRules(){
    _pageRules = new HashMap();
    _pageRules.put(ItemStatus.CHECKED_OUT, false);
    _pageRules.put(ItemStatus.AVAILABLE, true);
    _pageRules.put(ItemStatus.AWAITING_PICKUP, false);
    _pageRules.put(ItemStatus.IN_TRANSIT, false);
    _pageRules.put(ItemStatus.MISSING, false);
    _pageRules.put(ItemStatus.PAGED, false);
    _pageRules.put(ItemStatus.NONE, false);
  }

  public static boolean canCreateRequestForItem(ItemStatus itemStatus, RequestType requestType){
    if (requestType == RequestType.HOLD){
      return _holdRules.get(itemStatus);
    } else if (requestType == RequestType.RECALL){
      return _recallRules.get(itemStatus);
    } else if (requestType == RequestType.PAGE){
      return _pageRules.get(itemStatus);
    } else {
      return false;
    }
  }
}
