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
  private static final String CODE = "code";
  private static final String NAME = "name";
  private String expectedLibraryName;
  private String expectedLocationCode;
  private String expectedLocationName;

  public static Matcher<JsonObject> hasItemLocationProperties(
    String locationName, String libraryName, String locationCode ) {

    return new RequestItemMatcher(locationName, libraryName, locationCode);
  }

  private RequestItemMatcher(
      String locationName, String libraryName, String locationCode) {
    this.expectedLibraryName = libraryName;
    this.expectedLocationCode = locationCode;
    this.expectedLocationName = locationName;
  }

  @Override
  protected boolean matchesSafely(JsonObject jsonObject,
          Description mismatchDescription) {

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
    String locationName = location.getString(NAME);

    if (!Objects.equals(expectedLibraryName, libraryName)) {
      notMatchedKeys.put(LIBRARY_NAME, libraryName);
    }

    if (!Objects.equals(expectedLocationCode, locationCode)) {
      notMatchedKeys.put(CODE, locationCode);
    }

    if (!Objects.equals(expectedLocationName, locationName)) {
      notMatchedKeys.put(NAME, locationName);
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
      .put(CODE, expectedLocationCode)
      .put(NAME, expectedLocationName);

    description.appendText("item location : ")
      .appendValue(expectedLocation);
  }
}
