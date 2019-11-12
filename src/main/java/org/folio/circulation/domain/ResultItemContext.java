package org.folio.circulation.domain;

import org.folio.circulation.support.Result;

import java.util.List;

public class ResultItemContext {
  private final int currPageNumber;
  private final List<Result<MultipleRecords<Item>>> resultListOfItems;

  public ResultItemContext(int currPageNumber,
                           List<Result<MultipleRecords<Item>>> resultListOfItems) {
    this.currPageNumber = currPageNumber;
    this.resultListOfItems = resultListOfItems;
  }

  public int getCurrPageNumber() {
    return currPageNumber;
  }

  public List<Result<MultipleRecords<Item>>> getResultListOfItems() {
    return resultListOfItems;
  }

  public int getPageOffset(int pageLimit) {
    return currPageNumber * pageLimit;
  }
}
