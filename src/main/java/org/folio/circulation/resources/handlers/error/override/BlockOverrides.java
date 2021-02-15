package org.folio.circulation.resources.handlers.error.override;

import static java.util.Collections.emptyList;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.ITEM_LIMIT_BLOCK;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.ITEM_NOT_LOANABLE_BLOCK;
import static org.folio.circulation.resources.handlers.error.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedObjectProperty;

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
    return from(getNestedObjectProperty(
      requestRepresentation, "requestProcessingParameters", "overrideBlocks"));
  }

  public static BlockOverrides from(JsonObject representation) {
    if (representation == null) {
      return new BlockOverrides(emptyList(), null);
    }

    List<BlockOverride> overrides = new ArrayList<>();

    if (representation.getJsonObject(PATRON_BLOCK.getName()) != null) {
      overrides.add(new PatronBlockOverride());
    }

    if (representation.getJsonObject(ITEM_LIMIT_BLOCK.getName()) != null) {
      overrides.add(new ItemLimitBlockOverride());
    }

    JsonObject itemNotLoanableBlock = representation.getJsonObject(ITEM_NOT_LOANABLE_BLOCK.getName());
    if (itemNotLoanableBlock != null) {
      DateTime dueDate = getDateTimeProperty(itemNotLoanableBlock, "dueDate");
      overrides.add(new ItemNotLoanableBlockOverride(dueDate));
    }

    return new BlockOverrides(overrides, representation.getString("comment"));
  }

  public Optional<BlockOverride> findOverrideOfType(OverridableBlockType blockType) {
    return overrides.stream()
      .filter(override -> override.getBlockType() == blockType)
      .findFirst();
  }
}
