package org.folio.circulation.rules.cache;

import org.folio.circulation.rules.Drools;
import org.folio.rest.jaxrs.resource.Pubsub.PostPubsubEventTypesResponse;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Rules {
  private volatile String rulesAsText = "";
  private volatile String rulesAsDrools = "";
  private volatile Drools drools;
  /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
  private volatile long reloadTimestamp;

}
