package org.folio.circulation.domain;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.JsonArrayHelper;

import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Note {
  private final String id;
  private final String typeId;
  private final String domain;
  private final String title;
  private final String content;
  private final List<NoteLink> links;

  public Note(String id, String typeId, String domain,
    String title, String content, List<NoteLink> links) {

    this.id = id;
    this.typeId = typeId;
    this.domain = domain;
    this.title = title;
    this.content = content;
    this.links = links;
  }

  public static Note from(JsonObject jsonObject) {
    JsonArray noteLinksJson = getArrayProperty(jsonObject, "links");
    List<NoteLink> noteLinks = JsonArrayHelper.toStream(noteLinksJson)
      .map(NoteLink::from)
      .collect(Collectors.toList());

    return new Note(jsonObject.getString("id"), jsonObject.getString("typeId"),
      jsonObject.getString("domain"), jsonObject.getString("title"),
      jsonObject.getString("content"), noteLinks);
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();
    List<JsonObject> jsonLinks = links.stream()
      .map(NoteLink::toJson)
      .collect(Collectors.toList());

    if (StringUtils.isNotBlank(id)){
      jsonObject.put("id", id);
    }
    jsonObject.put("typeId", typeId);
    jsonObject.put("domain", domain);
    jsonObject.put("title", title);
    jsonObject.put("content", content);
    jsonObject.put("links", jsonLinks);

    return jsonObject;
  }

  String getId() {
    return id;
  }

  String getTypeId() {
    return typeId;
  }

  String getDomain() {
    return domain;
  }

  String getTitleString() {
    return title;
  }

  String getTitleContent() {
    return content;
  }

  List<NoteLink> getLinks() {
    return links;
  }
}
