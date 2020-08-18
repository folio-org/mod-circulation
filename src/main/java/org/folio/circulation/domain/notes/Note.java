package org.folio.circulation.domain.notes;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.NoteLink;

import io.vertx.core.json.JsonObject;
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
