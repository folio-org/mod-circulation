package org.folio.circulation.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class AllowedServicePoint {
  private String id;
  private String name;

  public AllowedServicePoint(ServicePoint servicePoint) {
    this.id = servicePoint.getId();
    this.name = servicePoint.getName();
  }
}
