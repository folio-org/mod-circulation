package org.folio.circulation.domain.notice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.builders.UserBuilder;

class PatronNoticeConfigurationResolverTest {

  @ParameterizedTest
  @MethodSource("preferenceCases")
  void selectsConfigurationsUsingContactTypePreferences(
    List<String> preferredContactTypeIds, String deprecatedPreferredContactTypeId,
    List<NoticeFormat> expectedFormats) {

    var user = buildUser(preferredContactTypeIds, deprecatedPreferredContactTypeId);
    var group = threeFormatGroup("email", "sms", "print");

    assertThat(formats(PatronNoticeConfigurationResolver.select(group, user)), is(expectedFormats));
  }

  static Stream<Arguments> preferenceCases() {
    return Stream.of(
      Arguments.of(List.of("002", "003"), "001", List.of(NoticeFormat.EMAIL, NoticeFormat.SMS)),
      Arguments.of(List.of("002"), "003", List.of(NoticeFormat.EMAIL)),
      Arguments.of(List.of(), "003", List.of(NoticeFormat.SMS)),
      Arguments.of(null, "001", List.of(NoticeFormat.PRINT)),
      Arguments.of(List.of(), null, List.of(NoticeFormat.EMAIL)),
      Arguments.of(null, null, List.of(NoticeFormat.EMAIL))
    );
  }

  @ParameterizedTest
  @MethodSource("singleFormatCases")
  void returnsSingleFormatRegardlessOfPreferences(NoticeFormat configuredFormat,
    List<String> preferredContactTypeIds) {

    var user = buildUser(preferredContactTypeIds, null);
    var configuration = configuration("template", configuredFormat);

    var selected = PatronNoticeConfigurationResolver.select(List.of(configuration), user);

    assertThat(selected, is(List.of(configuration)));
  }

  static Stream<Arguments> singleFormatCases() {
    return Stream.of(
      Arguments.of(NoticeFormat.EMAIL, List.of("003", "001")),
      Arguments.of(NoticeFormat.SMS, List.of("002")),
      Arguments.of(NoticeFormat.PRINT, List.of("002", "003"))
    );
  }

  @Test
  void preservesTemplateIdsWhenSelectingMultipleFormats() {
    var user = buildUser(List.of("002", "003"), null);
    var group = threeFormatGroup("email-template", "sms-template", "print-template");

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(selected, hasSize(2));
    assertThat(
      templateIds(selected),
      is(List.of("email-template", "sms-template")));
  }

  @Test
  void selectsAllPreferredFormatsInPreferenceOrder() {
    var user = buildUser(List.of("002", "001", "003"), null);
    var group = threeFormatGroup("email", "sms", "print");

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(
      formats(selected),
      is(List.of(NoticeFormat.EMAIL, NoticeFormat.PRINT, NoticeFormat.SMS)));
  }

  @Test
  void selectsOnlyEmailWhenPreferencesAreMissing() {
    var user = buildUser(List.of(), null);
    var group = threeFormatGroup("email", "sms", "print");

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(selected, hasSize(1));
    assertThat(selected.getFirst().getNoticeFormat(), is(NoticeFormat.EMAIL));
  }

  @Test
  void selectsFirstAvailableWhenPreferencesAndEmailAreMissing() {
    var user = buildUser(List.of(), null);
    var group = List.of(
      configuration("sms", NoticeFormat.SMS),
      configuration("print", NoticeFormat.PRINT));

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(selected, hasSize(1));
    assertThat(selected.getFirst().getNoticeFormat(), is(NoticeFormat.SMS));
  }

  @Test
  void fallsBackToEmailWhenArrayPreferenceIsNotConfigured() {
    var user = buildUser(List.of("003"), null);
    var group = List.of(
      configuration("email", NoticeFormat.EMAIL),
      configuration("print", NoticeFormat.PRINT));

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(selected, hasSize(1));
    assertThat(selected.getFirst().getNoticeFormat(), is(NoticeFormat.EMAIL));
  }

  @Test
  void fallsBackToEmailWhenDeprecatedPreferenceIsNotConfigured() {
    var user = buildUser(null, "003");
    var group = List.of(
      configuration("email", NoticeFormat.EMAIL),
      configuration("print", NoticeFormat.PRINT));

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(selected, hasSize(1));
    assertThat(selected.getFirst().getNoticeFormat(), is(NoticeFormat.EMAIL));
  }

  @Test
  void usesDeprecatedPreferenceWhenArrayHasNoUsableValues() {
    var user = buildUser(List.of("999"), "003");
    var group = threeFormatGroup("email", "sms", "print");

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(formats(selected), is(List.of(NoticeFormat.SMS)));
  }

  @Test
  void selectsEmailWhenNoPreferenceValueIsUsable() {
    var user = buildUser(List.of("999"), null);
    var group = threeFormatGroup("email", "sms", "print");

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(formats(selected), is(List.of(NoticeFormat.EMAIL)));
  }

  @Test
  void keepsFirstConfigurationForDuplicateFormat() {
    var firstEmail = configuration("first-email", NoticeFormat.EMAIL);
    var secondEmail = configuration("second-email", NoticeFormat.EMAIL);
    var sms = configuration("sms", NoticeFormat.SMS);
    var user = buildUser(List.of("002", "003"), null);

    var selected = PatronNoticeConfigurationResolver.select(
      List.of(firstEmail, secondEmail, sms), user);

    assertThat(selected, is(List.of(firstEmail, sms)));
  }

  @Test
  void keepsConfigurationsMatchingTheFirstMatchGroup() {
    var email = configuration("email", NoticeFormat.EMAIL);
    var sms = configuration("sms", NoticeFormat.SMS);
    var differentTiming = new NoticeConfiguration(
      "later-email",
      NoticeFormat.EMAIL,
      NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT,
      null,
      false,
      null,
      true);

    var group = PatronNoticeConfigurationResolver.firstMatchGroup(
      List.of(email, sms, differentTiming));

    assertThat(group, is(List.of(email, sms)));
  }

  @Test
  void selectsOnlyFromTheFirstMatchGroup() {
    var first = configuration("first", NoticeFormat.EMAIL);
    var second = new NoticeConfiguration(
      "second",
      NoticeFormat.EMAIL,
      NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT,
      null,
      false,
      null,
      true);

    var selected = PatronNoticeConfigurationResolver.select(List.of(first, second), null);

    assertThat(selected, is(List.of(first)));
  }

  @Test
  void returnsEmptyListForEmptyInput() {
    var user = buildUser(List.of("002"), null);

    assertThat(PatronNoticeConfigurationResolver.select(List.of(), user), empty());
  }

  @Test
  void returnsEmptyListForNullInput() {
    assertThat(PatronNoticeConfigurationResolver.select(null, null), empty());
  }

  @Test
  void doesNotRequirePreferencesForSingleFormat() {
    var group = List.of(configuration("email", NoticeFormat.EMAIL));

    assertThat(PatronNoticeConfigurationResolver.requiresPreference(group), is(false));
  }

  @Test
  void requiresPreferencesForMultipleFormats() {
    var group = List.of(
      configuration("email", NoticeFormat.EMAIL),
      configuration("sms", NoticeFormat.SMS));

    assertThat(PatronNoticeConfigurationResolver.requiresPreference(group), is(true));
  }

  @Test
  void ignoresUndeliverableFormatsWhenCountingFormats() {
    var email = configuration("email", NoticeFormat.EMAIL);
    var unknown = configuration("unknown", NoticeFormat.UNKNOWN);
    var user = buildUser(List.of("003"), null);
    var group = List.of(email, unknown);

    assertThat(PatronNoticeConfigurationResolver.requiresPreference(group), is(false));
    assertThat(PatronNoticeConfigurationResolver.select(group, user), is(List.of(email)));
  }

  @Test
  void returnsOnlyDeliverableFormats() {
    var user = buildUser(List.of("002", "003", "001"), null);
    var group = threeFormatGroup("email", "sms", "print");

    var selected = PatronNoticeConfigurationResolver.select(group, user);

    assertThat(selected, hasSize(3));
    assertTrue(selected.stream()
      .map(NoticeConfiguration::getNoticeFormat)
      .allMatch(NoticeFormat::isDeliverable));
  }


  @Test
  void matchGroupOfReturnsEmptyListWhenCandidatesAreNull() {
    var anchor = configuration("email", NoticeFormat.EMAIL);

    assertThat(PatronNoticeConfigurationResolver.matchGroupOf(null, anchor), empty());
  }

  @Test
  void matchGroupOfReturnsEmptyListWhenCandidatesAreEmpty() {
    var anchor = configuration("email", NoticeFormat.EMAIL);

    assertThat(PatronNoticeConfigurationResolver.matchGroupOf(List.of(), anchor), empty());
  }

  @Test
  void matchGroupOfReturnsEmptyListWhenAnchorIsNull() {
    var group = threeFormatGroup("email", "sms", "print");

    assertThat(PatronNoticeConfigurationResolver.matchGroupOf(group, null), empty());
  }

  @Test
  void matchGroupOfReturnsConfigurationsMatchingAnchorKey() {
    var email = configuration("email", NoticeFormat.EMAIL);
    var sms = configuration("sms", NoticeFormat.SMS);
    var differentTiming = new NoticeConfiguration(
      "later-email",
      NoticeFormat.EMAIL,
      NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT,
      null,
      false,
      null,
      true);

    var result = PatronNoticeConfigurationResolver.matchGroupOf(
      List.of(email, sms, differentTiming), email);

    assertThat(result, is(List.of(email, sms)));
  }

  @Test
  void matchGroupOfReturnsEmptyListWhenNothingMatchesAnchor() {
    var anchor = new NoticeConfiguration(
      "unique",
      NoticeFormat.EMAIL,
      NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT,
      null,
      false,
      null,
      true);
    var group = threeFormatGroup("email", "sms", "print");

    assertThat(PatronNoticeConfigurationResolver.matchGroupOf(group, anchor), empty());
  }

  @Test
  void firstMatchGroupReturnsEmptyListForNullInput() {
    assertThat(PatronNoticeConfigurationResolver.firstMatchGroup(null), empty());
  }

  @Test
  void firstMatchGroupReturnsEmptyListForEmptyInput() {
    assertThat(PatronNoticeConfigurationResolver.firstMatchGroup(List.of()), empty());
  }

  @Test
  void resolveFormatsReturnsFormatsOfSelectedConfigurations() {
    var user = buildUser(List.of("002", "003"), null);
    var group = threeFormatGroup("email", "sms", "print");

    var formats = PatronNoticeConfigurationResolver.resolveFormats(group, user);

    Set<NoticeFormat> expected = new LinkedHashSet<>(List.of(NoticeFormat.EMAIL, NoticeFormat.SMS));
    assertThat(formats, is(expected));
  }

  @Test
  void resolveFormatsReturnsEmptySetWhenGroupIsEmpty() {
    var user = buildUser(List.of("002"), null);

    assertThat(PatronNoticeConfigurationResolver.resolveFormats(List.of(), user), empty());
  }

  @Test
  void resolveFormatsReturnsSingleFormatWhenOnlyOneConfigured() {
    var user = buildUser(List.of(), null);
    var group = List.of(configuration("email", NoticeFormat.EMAIL));

    var formats = PatronNoticeConfigurationResolver.resolveFormats(group, user);

    assertThat(formats, is(Set.of(NoticeFormat.EMAIL)));
  }

  @Test
  void doesNotRequirePreferencesForEmptyGroup() {
    assertThat(PatronNoticeConfigurationResolver.requiresPreference(List.of()), is(false));
  }

  @Test
  void doesNotRequirePreferencesForNullGroup() {
    assertThat(PatronNoticeConfigurationResolver.requiresPreference(null), is(false));
  }

  private static User buildUser(List<String> preferredContactTypeIds,
    String deprecatedPreferredContactTypeId) {

    var builder = new UserBuilder();

    if (preferredContactTypeIds != null) {
      builder = builder.withPreferredContactTypeIds(preferredContactTypeIds);
    }

    if (deprecatedPreferredContactTypeId != null) {
      builder = builder.withDeprecatedPreferredContactTypeId(
        deprecatedPreferredContactTypeId);
    }

    return new User(builder.create());
  }

  private static List<NoticeConfiguration> threeFormatGroup(
    String emailTemplateId, String smsTemplateId, String printTemplateId) {

    return List.of(
      configuration(emailTemplateId, NoticeFormat.EMAIL),
      configuration(smsTemplateId, NoticeFormat.SMS),
      configuration(printTemplateId, NoticeFormat.PRINT));
  }

  private static NoticeConfiguration configuration(
    String templateId, NoticeFormat format) {

    return new NoticeConfiguration(
      templateId,
      format,
      NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER,
      Period.days(1),
      false,
      null,
      true);
  }

  private static List<NoticeFormat> formats(
    List<NoticeConfiguration> configurations) {

    return configurations.stream()
      .map(NoticeConfiguration::getNoticeFormat)
      .toList();
  }

  private static List<String> templateIds(
    List<NoticeConfiguration> configurations) {

    return configurations.stream()
      .map(NoticeConfiguration::getTemplateId)
      .toList();
  }
}
