package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RequestItemMatcher extends TypeSafeDiagnosingMatcher<JsonObject> {

  private static final String LIBRARY_NAME = "libraryName";
  private final String CODE = "code";
  private String expectedLibraryName;
  private String expectedLocationCode;

  public static Matcher<JsonObject> hasItemLocationProperties(
    String libraryName, String locationCode ) {

    return new RequestItemMatcher(libraryName, locationCode);
  }

  private RequestItemMatcher(
    String libraryName, String locationCode) {
    this.expectedLibraryName = libraryName;
    this.expectedLocationCode = locationCode;
  }

  @Override
  protected boolean matchesSafely(JsonObject jsonObject, Description mismatchDescription) {

    Map<String, String> notMatchedKeys = new HashMap<>();

    JsonObject item = jsonObject.getJsonObject("item");
    if (item == null) {
      mismatchDescription.appendText("item is null");
      return false;
    }

    JsonObject location = item.getJsonObject("location");
    if (location == null) {
      mismatchDescription.appendText("item location is null");
      return false;
    }
    String libraryName = location.getString(LIBRARY_NAME);
    String locationCode = location.getString(CODE);
    if (!Objects.equals(expectedLibraryName, libraryName)) {
      notMatchedKeys.put(LIBRARY_NAME, libraryName);
    }

    if (!Objects.equals(expectedLocationCode, locationCode)) {
      notMatchedKeys.put(CODE, locationCode);
    }
    if (!notMatchedKeys.isEmpty()) {
      String mismatchedKeysDescribing = notMatchedKeys.entrySet().stream()
        .map(e -> String.format("%s : %s", e.getKey(), e.getValue()))
        .collect(Collectors.joining(", "));
      mismatchDescription.appendText(mismatchedKeysDescribing);
      return false;
    }

    return true;
  }

  @Override
  public void describeTo(Description description) {
    JsonObject expectedLocation = new JsonObject()
      .put(LIBRARY_NAME, expectedLibraryName)
      .put(CODE, expectedLocationCode);

    description.appendText("item location : ")
      .appendValue(expectedLocation);
  }
}
