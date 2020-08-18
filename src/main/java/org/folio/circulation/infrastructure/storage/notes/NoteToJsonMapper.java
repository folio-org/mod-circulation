package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.notes.Note;
import org.folio.circulation.support.JsonArrayHelper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NoteToJsonMapper {

  private static NoteLink noteLinkFrom(JsonObject jsonObject) {
    return new NoteLink(jsonObject.getString("id"), jsonObject.getString("type"));
  }

  public static JsonObject toJson(Note note) {
    JsonObject jsonObject = new JsonObject();

    List<JsonObject> jsonLinks = note.getLinks().stream()
      .map(NoteToJsonMapper::toJson)
      .collect(Collectors.toList());

    if (StringUtils.isNotBlank(note.getId())) {
      jsonObject.put("id", note.getId());
    }

    jsonObject.put("typeId", note.getTypeId());
    jsonObject.put("domain", note.getDomain());
    jsonObject.put("title", note.getTitle());
    jsonObject.put("content", note.getContent());
    jsonObject.put("links", jsonLinks);

    return jsonObject;
  }

  public static JsonObject toJson(NoteLink link) {
    JsonObject jsonLink = new JsonObject();

    jsonLink.put("id", link.getId());
    jsonLink.put("type", link.getType());

    return jsonLink;
  }

  public Note noteFrom(JsonObject jsonObject) {
    JsonArray noteLinksJson = getArrayProperty(jsonObject, "links");
    List<NoteLink> noteLinks = JsonArrayHelper.toStream(noteLinksJson)
      .map(NoteToJsonMapper::noteLinkFrom)
      .collect(Collectors.toList());

    return new Note(jsonObject.getString("id"), jsonObject.getString("typeId"),
      jsonObject.getString("domain"), jsonObject.getString("title"),
      jsonObject.getString("content"), noteLinks);
  }
}
