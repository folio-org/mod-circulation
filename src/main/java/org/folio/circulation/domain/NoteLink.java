package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode
@Getter
@RequiredArgsConstructor
public class NoteLink {
  private final String id;
  private final String type;

  public static NoteLink from(String id, String type) {
    return new NoteLink(id, type);
  }

  public static NoteLink from(JsonObject jsonObject) {
    return new NoteLink(jsonObject.getString("id"), jsonObject.getString("type"));
  }

  public static JsonObject toJson(NoteLink link) {
    JsonObject jsonLink = new JsonObject();
    jsonLink.put("id", link.id);
    jsonLink.put("type", link.type);
    return jsonLink;
  }
}
