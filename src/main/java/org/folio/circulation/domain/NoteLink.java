package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class NoteLink extends JsonObject {
  String id;
  String type;

  public NoteLink(String id, String type) {
    super();
    this.id = id;
    this.type = type;
  }

  public static NoteLink from(String id, String type) {
    return new NoteLink(id, type);
  }
}
