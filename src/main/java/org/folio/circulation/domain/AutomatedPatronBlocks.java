package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public class AutomatedPatronBlocks {
  private final List<AutomatedPatronBlock> blocks;

  public AutomatedPatronBlocks() {
    blocks = new ArrayList<>();
  }

  private AutomatedPatronBlocks(List<AutomatedPatronBlock> blocks) {
    this.blocks = blocks;
  }

  public static AutomatedPatronBlocks from(JsonObject representation) {
    JsonArray automatedPatronBlocks = getArrayProperty(representation, "automatedPatronBlocks");

    return new AutomatedPatronBlocks(IntStream.range(0, automatedPatronBlocks.size())
      .mapToObj(automatedPatronBlocks::getJsonObject)
      .map(AutomatedPatronBlock::from)
      .collect(Collectors.toList()));
  }

  public List<AutomatedPatronBlock> getBlocks() {
    return blocks;
  }
}
