/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.actions;

import static com.rabbitmq.actions.DeleteReleaseAction.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.actions.DeleteReleaseAction.Release;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DeleteReleaseActionTest {

  static Release rTag(long id, String tag) {
    return new Release(id, tag, null);
  }

  static Release rName(long id, String name) {
    return new Release(id, null, name);
  }

  static Release rDate(long id, String date) {
    ZonedDateTime dateTime = null;
    if (date != null) {
      dateTime = ZonedDateTime.parse(date + "T08:38:25Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
    return new Release(id, dateTime);
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

    assertThat(filtered.stream().mapToLong(Release::id))
        .hasSize(4)
        .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

    filtered = filterByTag(releases, "^v([0-9].[0-9].[0-9]+-alpha.[0-9]+)$");

    assertThat(filtered.stream().mapToLong(Release::id))
        .hasSize(4)
        .containsExactlyInAnyOrder(10L, 11L, 12L, 13L);

    filtered = filterByTag(releases, "^v(3.8.[0-9]+-alpha.[0-9]+)$");

    assertThat(filtered.stream().mapToLong(Release::id))
        .hasSize(2)
        .containsExactlyInAnyOrder(10L, 11L);
  }

  @Test
  void filterByNameTest() {
    List<Release> releases =
        new java.util.ArrayList<>(
            List.of(
                rName(1, "RabbitMQ 4.0.5-alpha.c0e9eb0a (from 1732907131)"),
                rName(2, "RabbitMQ 4.0.5-alpha.5b90d1aa (from 1732900728)"),
                rName(10, "RabbitMQ 4.1.0-alpha.cead668b (from 1732900443)"),
                rName(3, "RabbitMQ 4.1.0-alpha.84607b7d (from 1732880145)"),
                rName(11, "RabbitMQ 4.0.5-alpha.1edb0477 (from 1732832426)"),
                rName(12, "RabbitMQ 4.1.0-alpha.534e4f18 (from 1732830884)"),
                rName(4, "RabbitMQ 4.1.0-alpha.d6366a3c (from 1732822478)"),
                rName(20, "RabbitMQ 4.0.0-beta (from 1732822478)"),
                rName(21, "RabbitMQ 4.1.0-beta (from 1732822478)"),
                rName(13, "RabbitMQ 4.1.0-alpha.7b2f5fbb (from 1732822415)")));

    Collections.shuffle(releases);

    List<Release> filtered = filterByName(releases, ".*4.0.[0-9]+-alpha.*");

    assertThat(filtered.stream().mapToLong(Release::id))
        .hasSize(3)
        .containsExactlyInAnyOrder(1L, 2L, 11L);

    filtered = filterByName(releases, ".*4.1.[0-9]+-alpha.*");

    assertThat(filtered.stream().mapToLong(Release::id))
        .hasSize(5)
        .containsExactlyInAnyOrder(10L, 3L, 12L, 4L, 13L);
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

  private static List<Release> filterByTag(List<Release> releases, String regex) {
    return DeleteReleaseAction.filter(releases, tagPredicate(regex));
  }

  private static List<Release> filterByName(List<Release> releases, String regex) {
    return DeleteReleaseAction.filter(releases, namePredicate(regex));
  }
}
