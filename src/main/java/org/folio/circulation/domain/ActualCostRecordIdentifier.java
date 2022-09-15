package org.folio.circulation.domain;

import java.util.Collection;

import io.vertx.core.json.JsonObject;
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

  public static ActualCostRecordIdentifier fromIdentifier(Identifier identifier,
    Collection<IdentifierType> identifierTypes) {

    return new ActualCostRecordIdentifier()
      .withIdentifierTypeId(identifier.getIdentifierTypeId())
      .withIdentifierType(identifierTypes.stream()
        .filter(type -> type.getId().equals(identifier.getIdentifierTypeId()))
        .findFirst()
        .map(IdentifierType::getName)
        .orElse(""))
      .withValue(identifier.getValue());
  }

  public static ActualCostRecordIdentifier fromRepresentation(JsonObject representation) {
    return new ActualCostRecordIdentifier()
      .withIdentifierTypeId(representation.getString("identifierTypeId"))
      .withIdentifierType(representation.getString("identifierType"))
      .withValue(representation.getString("value"));
  }
}
