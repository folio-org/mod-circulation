package org.folio.circulation.domain.notes;

import java.util.List;

import org.folio.circulation.domain.NoteLink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

@Builder
@Getter
@AllArgsConstructor
public class Note {
  private final String id;
  private final String typeId;
  private final String domain;
  private final String title;
  private final String content;
  @Singular
  private final List<NoteLink> links;

}
