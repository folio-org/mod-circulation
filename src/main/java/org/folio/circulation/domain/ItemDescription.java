package org.folio.circulation.domain;

import java.util.Collection;
import java.util.List;

import lombok.NonNull;
import lombok.Value;

@Value
public class ItemDescription {
  public static ItemDescription unknown() {
    return new ItemDescription(null, null, null, null, null, null, null, null, List.of(), null, List.of());
  }

  String barcode;
  String enumeration;
  String copyNumber;
  String volume;
  String chronology;
  String numberOfPieces;
  String descriptionOfPieces;
  String displaySummary;
  @NonNull Collection<String> yearCaption;
  String accessionNumber;
  @NonNull Collection<String> administrativeNotes;
}
