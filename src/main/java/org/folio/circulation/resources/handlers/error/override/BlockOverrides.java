package org.folio.circulation.resources.handlers.error.override;

import static java.util.Collections.emptyList;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.ITEM_LIMIT_BLOCK;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.ITEM_NOT_LOANABLE_BLOCK;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockOverrides {
  private final List<BlockOverride> overrides;
  private final String comment;

  public static BlockOverrides fromRequest(JsonObject requestRepresentation) {
    return from(
      Optional.ofNullable(requestRepresentation.getJsonObject("requestProcessingParameters"))
      .map(params -> params.getJsonObject("overrideBlocks"))
      .orElse(null));
  }

  public static BlockOverrides from(JsonObject representation) {
    if (representation == null) {
      return new BlockOverrides(emptyList(), null);
    }

    List<BlockOverride> overrides = new ArrayList<>();

    JsonObject patronBlockJson = representation.getJsonObject(PATRON_BLOCK.getName());
    if (patronBlockJson != null) {
      overrides.add(new PatronBlockOverride());
    }

    JsonObject itemLimitBlock = representation.getJsonObject(ITEM_LIMIT_BLOCK.getName());
    if (itemLimitBlock != null) {
      overrides.add(new ItemLimitBlockOverride());
    }

    JsonObject itemNotLoanableBlock = representation.getJsonObject(ITEM_NOT_LOANABLE_BLOCK.getName());
    if (itemNotLoanableBlock != null) {
      DateTime dueDate = getDateTimeProperty(itemNotLoanableBlock, "dueDate");
      overrides.add(new ItemNotLoanableBlockOverride(dueDate));
    }

    String comment = representation.getString("comment");

    return new BlockOverrides(overrides, comment);
  }

    public Optional<BlockOverride> getOverrideOfType(OverridableBlockType blockType) {
      return overrides.stream()
        .filter(override -> override.getBlockType() == blockType)
        .findFirst();
    }
}
