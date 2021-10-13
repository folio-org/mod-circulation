package org.folio.circulation.domain;

import java.util.List;

import org.folio.circulation.support.results.Result;

public class ItemsReportFetcher {
  private final int currPageNumber;
  private final List<Result<MultipleRecords<Item>>> resultListOfItems;

  public ItemsReportFetcher(int currPageNumber,
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
