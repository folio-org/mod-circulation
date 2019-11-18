package org.folio.circulation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
// Do not fail when an undefined property specified,
// e.g. when inventory-storage has introduced a new property,
// but circulation has not been updated
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectiveCallNumberComponents {

  private final String callNumber;
  private final String callNumberPrefix;
  private final String callNumberSuffix;

  @JsonCreator
  public EffectiveCallNumberComponents(
    @JsonProperty("callNumber") String callNumber,
    @JsonProperty("callNumberPrefix") String callNumberPrefix,
    @JsonProperty("callNumberSuffix") String callNumberSuffix) {

    this.callNumber = callNumber;
    this.callNumberPrefix = callNumberPrefix;
    this.callNumberSuffix = callNumberSuffix;
  }

  public String getCallNumber() {
    return callNumber;
  }

  public String getCallNumberPrefix() {
    return callNumberPrefix;
  }

  public String getCallNumberSuffix() {
    return callNumberSuffix;
  }
}
