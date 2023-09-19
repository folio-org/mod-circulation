package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class AllowedServicePointsRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private Request.Operation operation;
  private String requesterId;
  private String instanceId;
  private String itemId;
  private String requestId;

  public void updateWithRequestInformation(Request request) {
    log.debug("updateWithRequestInformation:: parameters request: {}", request);

    if (request != null) {
      log.info("updateWithRequestInformation:: request in not null");
      this.requesterId = request.getRequesterId();

      if (request.isItemLevel()) {
        this.itemId = request.getItemId();
      }

      if (request.isTitleLevel()) {
        this.instanceId = request.getInstanceId();
      }
    }
  }

  public boolean isImplyingItemStatusIgnore() {
    return operation == Request.Operation.REPLACE;
  }
}
