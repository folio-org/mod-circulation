package org.folio.circulation.domain;

import java.util.LinkedList;
import java.util.List;

import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;

public class InstanceRequestRelatedRecords {

  private List<Item> unsortedAvailableItems;
  private List<Item> unsortedUnavailableItems;
  private List<Item> sortedAvailableItems;
  private List<Item> sortedUnavailableItems;
  private List<Item> itemsWithoutLoans;
  private List<Item> itemsWithoutRequests;
  private RequestByInstanceIdRequest instanceLevelRequest;

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

  public List<Item> getCombinedSortedItemsList() {
    List<Item> combinedItemsList = new LinkedList<>();

    if (sortedAvailableItems != null)
      combinedItemsList.addAll(sortedAvailableItems);
    if (sortedUnavailableItems != null)
      combinedItemsList.addAll(sortedUnavailableItems);

    return combinedItemsList;
  }

  public RequestByInstanceIdRequest getInstanceLevelRequest() {
    return instanceLevelRequest;
  }

  public void setInstanceLevelRequest(RequestByInstanceIdRequest requestByInstanceIdRequest) {
    this.instanceLevelRequest = requestByInstanceIdRequest;
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
}
