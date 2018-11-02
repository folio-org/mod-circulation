/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;

/**
 *
 * @author kurt
 */
public class ServicePoint {
  private final JsonObject representation;
  
  public ServicePoint(JsonObject representation) {
    this.representation = representation;
  }
  
  public Boolean isPickupLocation() {
    return representation.getBoolean("pickupLocation");
  }
  
  public String getName() {
    return getProperty(representation, "name");
  }
  
  public String getId() {
    return getProperty(representation, "id");
  }
  
  public String getCode() {
    return getProperty(representation, "code");
  }
  
  public String getDiscoveryDisplayName() {
    return getProperty(representation, "discoveryDisplayName");
  }
  
  public String getDescription() {
    return getProperty(representation, "description");
  }
  
  public Integer getShelvingLagTime() {
    return getIntegerProperty(representation, "shelvingLagTime", null);
  }
  
  public Boolean getPickupLocation() {
    return getBooleanProperty(representation, "pickupLocation");
  }
}
