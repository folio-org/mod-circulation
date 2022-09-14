package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecordIdentifier {
  private String value;
  private String identifierTypeId;
  private String identifierType;

  public static ActualCostRecordIdentifier fromIdentifier(Identifier identifier) {
    return new ActualCostRecordIdentifier()
      .withIdentifierTypeId(identifier.getIdentifierTypeId())
      .withIdentifierType("")
      .withValue(identifier.getValue());
  }
}
