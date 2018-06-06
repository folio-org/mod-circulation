package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;

public class BufferHelper {
  private BufferHelper() { }

  public static String stringFromBuffer(Buffer buffer) {
    if(buffer.length() == 0) {
      return "";
    }

    return buffer.getString(0, buffer.length());
  }
}
