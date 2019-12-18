package api.support.http;

public class Offset {
  private final int offset;

  public Offset(int offset) {
    this.offset = offset;
  }

  public static Offset offset(int offset) {
    return new Offset(offset);
  }

  public int getOffset() {
    return offset;
  }
}
