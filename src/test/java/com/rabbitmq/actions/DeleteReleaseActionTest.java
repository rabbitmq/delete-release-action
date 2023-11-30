/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.actions;

import static com.rabbitmq.actions.DeleteReleaseAction.filterByTag;
import static com.rabbitmq.actions.DeleteReleaseAction.filterForDeletion;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.actions.DeleteReleaseAction.Release;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DeleteReleaseActionTest {

  static Release rTag(long id, String tag) {
    return new Release(id, tag);
  }

  static Release rDate(long id, String date) {
    ZonedDateTime dateTime = null;
    if (date != null) {
     dateTime = ZonedDateTime.parse(date + "T08:38:25Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
    return new Release(
        id, dateTime);
  }

  @Test
  void filterByTagTest() {
    List<Release> releases =
        Arrays.asList(
            rTag(1, "v3.9.0-alpha-stream.1"),
            rTag(2, "v3.9.0-alpha-stream.2"),
            rTag(10, "v3.8.14-alpha.2"),
            rTag(3, "v3.9.0-alpha-stream.3"),
            rTag(11, "v3.8.9-alpha.8"),
            rTag(12, "v3.9.0-alpha.470"),
            rTag(4, "v3.9.0-alpha-stream.4"),
            rTag(13, "v4.0.0-alpha.51"));

    List<Release> filtered = filterByTag(releases, "^v(3.9.0-alpha-stream.[0-9]+)$");

    assertThat(filtered.stream().mapToLong(r -> r.id()))
        .hasSize(4)
        .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

    filtered = filterByTag(releases, "^v([0-9].[0-9].[0-9]+-alpha.[0-9]+)$");

    assertThat(filtered.stream().mapToLong(r -> r.id()))
        .hasSize(4)
        .containsExactlyInAnyOrder(10L, 11L, 12L, 13L);

    filtered = filterByTag(releases, "^v(3.8.[0-9]+-alpha.[0-9]+)$");

    assertThat(filtered.stream().mapToLong(r -> r.id()))
        .hasSize(2)
        .containsExactlyInAnyOrder(10L, 11L);
  }

  @Test
  void filterForDeletionTest() {
    List<Release> releases =
        Arrays.asList(
            rDate(1L, "2021-01-01"),
            rDate(3L, "2021-01-03"),
            rDate(4L, "2021-01-04"),
            rDate(2L, "2021-01-02"),
            rDate(42L, null),
            rDate(8L, "2021-01-08"),
            rDate(6L, "2021-01-06"),
            rDate(9L, "2021-01-09"),
            rDate(7L, "2021-01-07"),
            rDate(5L, "2021-01-05"));

    assertThat(filterForDeletion(releases, 3).stream().mapToLong(r -> r.id()))
        .hasSize(7)
        .containsExactlyInAnyOrder(42L, 1L, 2L, 3L, 4L, 5L, 6L);

    assertThat(filterForDeletion(releases, releases.size() - 1).stream().mapToLong(r -> r.id()))
        .hasSize(1)
        .containsExactlyInAnyOrder(42L);

    assertThat(filterForDeletion(releases, releases.size() - 2).stream().mapToLong(r -> r.id()))
        .hasSize(2)
        .containsExactlyInAnyOrder(42L, 1L);

    assertThat(filterForDeletion(releases, 0)).hasSameSizeAs(releases).hasSameElementsAs(releases);

    assertThat(filterForDeletion(releases, releases.size() + 1)).isEmpty();
  }

}
