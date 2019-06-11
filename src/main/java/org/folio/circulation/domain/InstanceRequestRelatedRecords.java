package org.folio.circulation.domain;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.joda.time.DateTime;

public class InstanceRequestRelatedRecords {

  private List<Item> unsortedAvailableItems;
  private List<Item> unsortedUnavailableItems;
  private List<Item> sortedAvailableItems;
  private List<Item> sortedUnavailableItems;
  private Map<String, DateTime> itemIdDueDateMap;
  private Map<Item, Integer> itemQueueSizeMap;
  private RequestByInstanceIdRequest requestByInstanceIdRequest;
  private List<Item> itemsWithoutLoans;
  private List<Item> itemsWithoutRequests;

  public List<Item> getUnsortedAvailableItems() {
    return unsortedAvailableItems;
  }

  public void setUnsortedAvailableItems(List<Item> unsortedAvailableItems) {
    this.unsortedAvailableItems = unsortedAvailableItems;
  }

  public List<Item> getUnsortedUnavailableItems() {
    return unsortedUnavailableItems;
  }

  public void setUnsortedUnavailableItems(List<Item> unsortedUnavailableItems) {
    this.unsortedUnavailableItems = unsortedUnavailableItems;
  }

  public void setSortedAvailableItems(List<Item> sortedAvailableItems) {
    this.sortedAvailableItems = sortedAvailableItems;
  }

  public List<Item> getSortedAvailableItems() {
    return sortedAvailableItems;
  }

  public void setSortedUnavailableItems(List<Item> sortedUnavailableItems) {
    this.sortedUnavailableItems = sortedUnavailableItems;
  }

  public List<Item> getCombineItemsList() {
    List<Item> combinedItemsList = new LinkedList<>();

    if (sortedAvailableItems != null)
      combinedItemsList.addAll(sortedAvailableItems);
    if (sortedUnavailableItems != null)
      combinedItemsList.addAll(sortedUnavailableItems);

    return combinedItemsList;
  }

  public RequestByInstanceIdRequest getRequestByInstanceIdRequest() {
    return requestByInstanceIdRequest;
  }

  public void setRequestByInstanceIdRequest(RequestByInstanceIdRequest requestByInstanceIdRequest) {
    this.requestByInstanceIdRequest = requestByInstanceIdRequest;
  }

  public Map<String, DateTime> getItemIdDueDateMap() {
    return itemIdDueDateMap;
  }

  public void setItemIdDueDateMap(Map<String, DateTime> itemIdDueDateMap) {
    this.itemIdDueDateMap = itemIdDueDateMap;
  }

  public List<Item> getItemsWithoutLoans() {
    return itemsWithoutLoans;
  }

  public void setItemsWithoutLoans(List<Item> itemsWithoutLoans) {
    this.itemsWithoutLoans = itemsWithoutLoans;
  }

  public List<Item> getItemsWithoutRequests() {
    return itemsWithoutRequests;
  }

  public void setItemsWithoutRequests(List<Item> itemsWithoutRequests) {
    this.itemsWithoutRequests = itemsWithoutRequests;
  }

  public Map<Item, Integer> getItemQueueSizeMap() {
    return itemQueueSizeMap;
  }

  public void setItemQueueSizeMap(Map<Item, Integer> itemQueueSizeMap) {
    this.itemQueueSizeMap = itemQueueSizeMap;
  }
}
