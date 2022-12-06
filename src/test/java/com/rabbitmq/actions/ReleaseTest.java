/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.actions;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.actions.DeleteReleaseAction.Release;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class ReleaseTest {

  static final String SAMPLE =
      "{\n"
          + "    \"url\": \"https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/releases/39045306\",\n"
          + "    \"assets_url\": \"https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/releases/39045306/assets\",\n"
          + "    \"upload_url\": \"https://uploads.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/releases/39045306/assets{?name,label}\",\n"
          + "    \"html_url\": \"https://github.com/rabbitmq/rabbitmq-server-binaries-dev/releases/tag/v3.9.0-alpha-stream.4\",\n"
          + "    \"id\": 39045306,\n"
          + "    \"author\": {\n"
          + "      \"login\": \"rabbitmq-ci\",\n"
          + "      \"id\": 71012429,\n"
          + "      \"node_id\": \"MDQ6VXNlcjcxMDEyNDI5\",\n"
          + "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/71012429?v=4\",\n"
          + "      \"gravatar_id\": \"\",\n"
          + "      \"url\": \"https://api.github.com/users/rabbitmq-ci\",\n"
          + "      \"html_url\": \"https://github.com/rabbitmq-ci\",\n"
          + "      \"followers_url\": \"https://api.github.com/users/rabbitmq-ci/followers\",\n"
          + "      \"following_url\": \"https://api.github.com/users/rabbitmq-ci/following{/other_user}\",\n"
          + "      \"gists_url\": \"https://api.github.com/users/rabbitmq-ci/gists{/gist_id}\",\n"
          + "      \"starred_url\": \"https://api.github.com/users/rabbitmq-ci/starred{/owner}{/repo}\",\n"
          + "      \"subscriptions_url\": \"https://api.github.com/users/rabbitmq-ci/subscriptions\",\n"
          + "      \"organizations_url\": \"https://api.github.com/users/rabbitmq-ci/orgs\",\n"
          + "      \"repos_url\": \"https://api.github.com/users/rabbitmq-ci/repos\",\n"
          + "      \"events_url\": \"https://api.github.com/users/rabbitmq-ci/events{/privacy}\",\n"
          + "      \"received_events_url\": \"https://api.github.com/users/rabbitmq-ci/received_events\",\n"
          + "      \"type\": \"User\",\n"
          + "      \"site_admin\": false\n"
          + "    },\n"
          + "    \"node_id\": \"MDc6UmVsZWFzZTM5MDQ1MzA2\",\n"
          + "    \"tag_name\": \"v3.9.0-alpha-stream.4\",\n"
          + "    \"target_commitish\": \"master\",\n"
          + "    \"name\": \"3.9.0-alpha-stream.4\",\n"
          + "    \"draft\": false,\n"
          + "    \"prerelease\": true,\n"
          + "    \"created_at\": \"2021-03-01T08:38:25Z\",\n"
          + "    \"published_at\": \"2021-03-01T10:37:58Z\",\n"
          + "    \"assets\": [\n"
          + "      {\n"
          + "        \"url\": \"https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/releases/assets/32772090\",\n"
          + "        \"id\": 32772090,\n"
          + "        \"node_id\": \"MDEyOlJlbGVhc2VBc3NldDMyNzcyMDkw\",\n"
          + "        \"name\": \"rabbitmq-server-generic-unix-3.9.0-alpha-stream.4.tar.xz\",\n"
          + "        \"label\": \"\",\n"
          + "        \"uploader\": {\n"
          + "          \"login\": \"rabbitmq-ci\",\n"
          + "          \"id\": 71012429,\n"
          + "          \"node_id\": \"MDQ6VXNlcjcxMDEyNDI5\",\n"
          + "          \"avatar_url\": \"https://avatars.githubusercontent.com/u/71012429?v=4\",\n"
          + "          \"gravatar_id\": \"\",\n"
          + "          \"url\": \"https://api.github.com/users/rabbitmq-ci\",\n"
          + "          \"html_url\": \"https://github.com/rabbitmq-ci\",\n"
          + "          \"followers_url\": \"https://api.github.com/users/rabbitmq-ci/followers\",\n"
          + "          \"following_url\": \"https://api.github.com/users/rabbitmq-ci/following{/other_user}\",\n"
          + "          \"gists_url\": \"https://api.github.com/users/rabbitmq-ci/gists{/gist_id}\",\n"
          + "          \"starred_url\": \"https://api.github.com/users/rabbitmq-ci/starred{/owner}{/repo}\",\n"
          + "          \"subscriptions_url\": \"https://api.github.com/users/rabbitmq-ci/subscriptions\",\n"
          + "          \"organizations_url\": \"https://api.github.com/users/rabbitmq-ci/orgs\",\n"
          + "          \"repos_url\": \"https://api.github.com/users/rabbitmq-ci/repos\",\n"
          + "          \"events_url\": \"https://api.github.com/users/rabbitmq-ci/events{/privacy}\",\n"
          + "          \"received_events_url\": \"https://api.github.com/users/rabbitmq-ci/received_events\",\n"
          + "          \"type\": \"User\",\n"
          + "          \"site_admin\": false\n"
          + "        },\n"
          + "        \"content_type\": \"application/octet-stream\",\n"
          + "        \"state\": \"uploaded\",\n"
          + "        \"size\": 21,\n"
          + "        \"download_count\": 2,\n"
          + "        \"created_at\": \"2021-03-01T10:37:58Z\",\n"
          + "        \"updated_at\": \"2021-03-01T10:37:58Z\",\n"
          + "        \"browser_download_url\": \"https://github.com/rabbitmq/rabbitmq-server-binaries-dev/releases/download/v3.9.0-alpha-stream.4/rabbitmq-server-generic-unix-3.9.0-alpha-stream.4.tar.xz\"\n"
          + "      }\n"
          + "    ],\n"
          + "    \"tarball_url\": \"https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/tarball/v3.9.0-alpha-stream.4\",\n"
          + "    \"zipball_url\": \"https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/zipball/v3.9.0-alpha-stream.4\",\n"
          + "    \"body\": \"\"\n"
          + "  }";

  @Test
  void deserialize() {
    Release release = DeleteReleaseAction.GSON.fromJson(SAMPLE, Release.class);
    assertThat(release.url())
        .isEqualTo(
            "https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/releases/39045306");
    assertThat(release.id()).isEqualTo(39045306);
    assertThat(release.tag()).isEqualTo("v3.9.0-alpha-stream.4");
    assertThat(release.publication())
        .isEqualTo(
            ZonedDateTime.parse("2021-03-01T10:37:58Z", DateTimeFormatter.ISO_ZONED_DATE_TIME));
  }
}
