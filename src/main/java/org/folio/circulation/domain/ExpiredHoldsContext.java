package org.folio.circulation.domain;

import org.folio.circulation.support.Result;

import java.util.List;

public class ExpiredHoldsContext {
  private final int currPageNumber;
  private final List<Result<MultipleRecords<Item>>> itemIds;

  public ExpiredHoldsContext(int currPageNumber,
                             List<Result<MultipleRecords<Item>>> itemIds) {
    this.currPageNumber = currPageNumber;
    this.itemIds = itemIds;
  }

  public int getCurrPageNumber() {
    return currPageNumber;
  }

  public List<Result<MultipleRecords<Item>>> getItemIds() {
    return itemIds;
  }

  public int getPageOffset(int pageLimit) {
    return currPageNumber * pageLimit;
  }
}
