package org.folio.circulation.domain;

public class InTransitReportEntry {
  private final Item item;
  private Request request;
  private Loan loan;

  public InTransitReportEntry(Item item) {
    this.item = item;
  }

  public Item getItem() {
    return item;
  }

  public Request getRequest() {
    return request;
  }

  public void setRequest(Request request) {
    this.request = request;
  }

  public Loan getLoan() {
    return loan;
  }

  public void setLoan(Loan loan) {
    this.loan = loan;
  }
}
