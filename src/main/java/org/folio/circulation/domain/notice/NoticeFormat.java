package org.folio.circulation.domain.notice;

import java.util.Arrays;

public enum NoticeFormat {

  EMAIL("Email", "email", "text/html"),
  PRINT("Print", "mail", "text/html"),
  UNKNOWN("Unknown", "", "");


  public static NoticeFormat from(String value) {
    return Arrays.stream(values())
      .filter(v -> v.getRepresentation().equals(value))
      .findFirst()
      .orElse(UNKNOWN);
  }

  private String representation;
  private String deliveryChannel;
  private String outputFormat;

  NoticeFormat(String value, String deliveryChannel, String outputFormat) {
    this.representation = value;
    this.deliveryChannel = deliveryChannel;
    this.outputFormat = outputFormat;
  }

  public String getDeliveryChannel() {
    return deliveryChannel;
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  public String getRepresentation() {
    return representation;
  }
}
