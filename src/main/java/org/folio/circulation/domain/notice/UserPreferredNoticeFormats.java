package org.folio.circulation.domain.notice;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.folio.circulation.domain.User;

public final class UserPreferredNoticeFormats {
  public static final String CONTACT_TYPE_EMAIL = "002";
  public static final String CONTACT_TYPE_MAIL = "001";
  public static final String CONTACT_TYPE_SMS = "003";

  private static final Map<String, NoticeFormat> CONTACT_TYPE_FORMATS = Map.of(
    CONTACT_TYPE_EMAIL, NoticeFormat.EMAIL,
    CONTACT_TYPE_MAIL, NoticeFormat.PRINT,
    CONTACT_TYPE_SMS, NoticeFormat.SMS
  );

  private UserPreferredNoticeFormats() {
  }

  public static Optional<NoticeFormat> toFormat(String contactTypeId) {
    if (contactTypeId == null || contactTypeId.isBlank()) {
      return Optional.empty();
    }

    return Optional.ofNullable(CONTACT_TYPE_FORMATS.get(contactTypeId));
  }

  public static List<NoticeFormat> fromPreferredContactTypeIds(User user) {
    if (user == null) {
      return List.of();
    }

    var formats = new LinkedHashSet<NoticeFormat>();
    user.getPreferredContactTypeIds().stream()
      .map(UserPreferredNoticeFormats::toFormat)
      .flatMap(Optional::stream)
      .forEach(formats::add);

    return List.copyOf(formats);
  }

  public static Optional<NoticeFormat> fromDeprecatedPreferredContactTypeId(User user) {
    if (user == null) {
      return Optional.empty();
    }

    return toFormat(user.getPreferredContactTypeId());
  }
}
