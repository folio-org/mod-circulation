package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class NoteLink {
  String id;
  String type;

  public NoteLink(String id, String type) {
    this.id = id;
    this.type = type;
  }

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
