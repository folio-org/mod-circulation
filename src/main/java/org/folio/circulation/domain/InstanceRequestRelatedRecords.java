package org.folio.circulation.domain;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;

public class InstanceRequestRelatedRecords {

  private List<Item> unsortedAvailableItems;
  private List<Item> unsortedUnavailableItems;
  private List<Item> sortedAvailableItems;
  private List<Item> sortedUnavailableItems;
  private RequestByInstanceIdRequest requestByInstanceIdRequest;

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
}
