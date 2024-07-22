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
  private String patronGroupId;
  private String instanceId;
  private String itemId;
  private String requestId;
  private boolean useStubItem;
  private boolean ecsRequestRouting;

  public boolean isForTitleLevelRequest() {
    return instanceId != null;
  }

  public boolean isForItemLevelRequest() {
    return itemId != null;
  }

  public AllowedServicePointsRequest updateWithRequestInformation(Request request) {
    log.debug("updateWithRequestInformation:: parameters request: {}", request);

    if (request != null) {
      log.info("updateWithRequestInformation:: request in not null");
      this.requesterId = request.getRequesterId();

      if (request.isItemLevel() && this.operation == Request.Operation.REPLACE) {
        this.itemId = request.getItemId();
      }

      if (request.isTitleLevel() && this.operation == Request.Operation.REPLACE) {
        this.instanceId = request.getInstanceId();
      }
    }

    return this;
  }

  public boolean isImplyingItemStatusIgnore() {
    return operation == Request.Operation.REPLACE;
  }
}
