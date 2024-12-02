/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.actions;

import static com.rabbitmq.actions.Utils.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DeleteReleaseAction {

  private static final String GITHUB_API_URL =
      System.getenv("GITHUB_API_URL") == null
          ? "https://api.github.com"
          : System.getenv("GITHUB_API_URL");
  static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeDeserializer())
          .create();

  public static void main(String[] args) {
    if (args.length == 1 && "test".equals(args[0])) {
      testSequence();
    }
    Map<String, String> envArguments = new LinkedHashMap<>();
    envArguments.put("INPUT_REPOSITORY", "repository");
    envArguments.put("INPUT_TOKEN", "token");
    envArguments.put("INPUT_TAG-FILTER", "tag-filter");
    envArguments.put("INPUT_KEEP-LAST-N", "keep-last-n");
    for (Entry<String, String> entry : envArguments.entrySet()) {
      try {
        checkParameter(entry.getKey(), entry.getValue());
      } catch (IllegalArgumentException e) {
        logRed(e.getMessage());
        System.exit(1);
      }
    }

    if (System.getenv("INPUT_TAG-FILTER") == null && System.getenv("INPUT_NAME-FILTER") == null) {
      logRed("Parameter tag-filter or name-filter must be set");
      System.exit(1);
    }

    String orgRepository = System.getenv("INPUT_REPOSITORY");
    String token = System.getenv("INPUT_TOKEN");
    String tagFilter = System.getenv("INPUT_TAG-FILTER");
    String nameFilter = System.getenv("INPUT_NAME-FILTER");
    int keepLastN = Integer.parseInt(System.getenv("INPUT_KEEP-LAST-N"));

    Input input =
        new Input(
            new Params(tagFilter, nameFilter, keepLastN),
            new Source(orgRepository.split("/")[0], orgRepository.split("/")[1], token));

    ReleaseAccess access = new GitubRestApiReleaseAccess(input);

    List<Release> releases = access.list();

    if (releases.isEmpty()) {
      logGreen("No releases in the repository.");
    } else {
      List<Release> filteredReleases = filter(releases, tagFilter, nameFilter);
      if (!filteredReleases.isEmpty()) {
        sortByPublication(filteredReleases);
      }
      List<Release> toDeleteReleases =
          filterForDeletion(filteredReleases, input.params().keepLastN());

      if (tagFilter == null) {
        logGreen("No tag filter.");
      } else {
        logGreen("Tag filter: %s.", tagFilter);
      }
      if (nameFilter == null) {
        logGreen("No name filter.");
      } else {
        logGreen("Name filter: %s.", nameFilter);
      }

      Function<Release, String> releaseSummary = r -> r.tag_name + "/" + r.name;
      logGreen(
          "Repository release(s): %d (%s).",
          releases.size(), releases.stream().map(releaseSummary).collect(joining(", ")));

      if (filteredReleases.isEmpty()) {
        logGreen("No selected releases.");
      } else {
        logGreen(
            "Selected release(s): %d (%s)",
            filteredReleases.size(),
            filteredReleases.stream().map(releaseSummary).collect(joining(", ")));
      }

      if (toDeleteReleases.isEmpty()) {
        logGreen("No releases to delete.");
      } else {
        logGreen(
            "Release(s) to delete: %d (%s)",
            toDeleteReleases.size(),
            toDeleteReleases.stream().map(releaseSummary).collect(joining(", ")));
      }

      filteredReleases.forEach(
          r -> {
            if (toDeleteReleases.contains(r)) {
              logYellow("Removing release '%s'", releaseSummary.apply(r));
              try {
                access.delete(r);
                access.deleteTag(r);
                access.waitForDeletion(r);
              } catch (Exception e) {
                logRed(
                    "Error while deleting release '%s': %s",
                    releaseSummary.apply(r), e.getMessage());
              }
            } else {
              log(" Keeping release '%s'", releaseSummary.apply(r));
            }
          });
    }
  }

  private static void checkParameter(String env, String arg) {
    if (System.getenv(env) == null) {
      throw new IllegalArgumentException("Parameter " + arg + " must be set");
    }
  }

  static Predicate<Release> releaseRegexPredicate(
      Function<Release, String> accessor, String regex) {
    Pattern pattern = Pattern.compile(regex);
    return r -> pattern.matcher(accessor.apply(r)).matches();
  }

  static Predicate<Release> tagPredicate(String tagRegex) {
    return releaseRegexPredicate(Release::tag, tagRegex);
  }

  static Predicate<Release> namePredicate(String nameRegex) {
    return releaseRegexPredicate(Release::name, nameRegex);
  }

  static List<Release> filter(List<Release> releases, String tagRegex, String nameRegex) {
    Predicate<Release> predicate = r -> true;
    if (tagRegex != null) {
      predicate = predicate.and(tagPredicate(tagRegex));
    }
    if (nameRegex != null) {
      predicate = predicate.and(namePredicate(nameRegex));
    }
    return filter(releases, predicate);
  }

  static List<Release> filter(List<Release> releases, Predicate<Release> predicate) {
    return releases.stream().filter(predicate).collect(toList());
  }

  static List<Release> filterForDeletion(List<Release> releases, int keepLastN) {
    if (releases.isEmpty()) {
      return Collections.emptyList();
    } else if (keepLastN <= 0) {
      // do not want to keep any, return all
      return releases;
    } else if (keepLastN >= releases.size()) {
      // we want to keep more than we have, so nothing to delete
      return Collections.emptyList();
    } else {
      releases = new ArrayList<>(releases);
      sortByPublication(releases);
      return releases.subList(0, releases.size() - keepLastN);
    }
  }

  interface ReleaseAccess {

    List<Release> list();

    void delete(Release release);

    void deleteTag(Release release);

    void waitForDeletion(Release release);
  }

  static class GitubRestApiReleaseAccess implements ReleaseAccess {

    private static final Duration DELETION_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

    private final Input input;

    GitubRestApiReleaseAccess(Input input) {
      this.input = input;
    }

    static String nextLink(String linkHeader) {
      String nextLink = null;
      for (String link : linkHeader.split(",")) {
        // e.g.
        // <https://api.github.com/repositories/343344332/releases?per_page=1&page=3>; rel="next"
        String[] urlRel = link.split(";");
        if ("rel=\"next\"".equals(urlRel[1].trim())) {
          String url = urlRel[0].trim();
          // removing the < and >
          nextLink = url.substring(1, url.length() - 1);
        }
      }
      return nextLink;
    }

    @Override
    public List<Release> list() {
      HttpRequest request = requestBuilder("/releases").GET().build();
      try {
        Type type = TypeToken.getParameterized(List.class, Release.class).getType();
        List<Release> releases = new ArrayList<>();
        boolean hasMore = true;
        while (hasMore) {
          HttpResponse<String> response =
              client.send(request, HttpResponse.BodyHandlers.ofString());
          releases.addAll(GSON.fromJson(response.body(), type));
          Optional<String> link = response.headers().firstValue("link");
          String nextLink;
          if (link.isPresent() && (nextLink = nextLink(link.get())) != null) {
            request = requestBuilder().uri(URI.create(nextLink)).GET().build();
          } else {
            hasMore = false;
          }
        }
        return releases;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void delete(Release release) {
      HttpRequest request = requestBuilder().DELETE().uri(URI.create(release.url())).build();
      try {
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        int statusCode = response.statusCode();
        if (statusClass(statusCode) != 200) {
          logYellow("Unexpected response code (release deletion):" + response.statusCode());
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void deleteTag(Release release) {
      HttpRequest request = requestBuilder().uri(tagUri(release)).DELETE().build();
      try {
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        int statusCode = response.statusCode();
        if (statusClass(statusCode) != 200) {
          logYellow("Unexpected response code (tag deletion):" + response.statusCode());
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private URI tagUri(Release release) {
      // https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/git/refs/tags/v3.9.0-alpha-test.1
      String path = "/git/refs/tags/" + release.tag();
      return URI.create(
          GITHUB_API_URL
              + "/repos/"
              + input.source().owner()
              + "/"
              + input.source().repository()
              + path);
    }

    @Override
    public void waitForDeletion(Release release) {
      if (!getUntilNotFound(URI.create(release.url()))) {
        logYellow(
            "  Release has not been deleted after "
                + DELETION_TIMEOUT.getSeconds()
                + " second(s).");
      }
      if (!getUntilNotFound(tagUri(release))) {
        logYellow(
            "  Tag has not been deleted after " + DELETION_TIMEOUT.getSeconds() + " second(s).");
      }
    }

    private boolean getUntilNotFound(URI uri) {
      Duration increment = Duration.ofSeconds(1);
      boolean keepGoing = true;
      Duration elapsed = Duration.ZERO;
      while (keepGoing && elapsed.compareTo(DELETION_TIMEOUT) < 0) {
        HttpRequest request = requestBuilder().GET().uri(uri).build();
        try {
          HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
          if (response.statusCode() == 404) {
            keepGoing = false;
          } else {
            Thread.sleep(increment.toMillis());
            elapsed = elapsed.plus(increment);
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      return !keepGoing;
    }

    private Builder requestBuilder() {
      return auth(HttpRequest.newBuilder());
    }

    private Builder requestBuilder(String path) {
      return auth(
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      GITHUB_API_URL
                          + "/repos/"
                          + input.source().owner()
                          + "/"
                          + input.source().repository()
                          + path)));
    }

    private Builder auth(Builder builder) {
      return builder.setHeader("Authorization", "token " + input.source().token());
    }

    private static int statusClass(int statusCode) {
      return statusCode - statusCode % 100;
    }
  }

  static class ZonedDateTimeDeserializer implements JsonDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return ZonedDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
  }

  static class Release {

    private long id;
    private String url;
    private ZonedDateTime published_at;
    private String tag_name;
    private String name;

    Release() {}

    Release(long id, String tag, String name) {
      this.id = id;
      this.tag_name = tag;
      this.name = name;
    }

    Release(long id, ZonedDateTime published_at) {
      this.id = id;
      this.published_at = published_at;
    }

    long id() {
      return this.id;
    }

    String url() {
      return this.url;
    }

    String tag() {
      return this.tag_name;
    }

    String name() {
      return this.name;
    }

    ZonedDateTime publication() {
      return this.published_at;
    }

    @Override
    public String toString() {
      return "Release{"
          + "id="
          + id
          + ", url='"
          + url
          + '\''
          + ", published_at="
          + published_at
          + ", tag_name='"
          + tag_name
          + '\''
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Release release = (Release) o;
      return id == release.id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  static class Input {

    private final Params params;
    private final Source source;

    Input(Params params, Source source) {
      this.params = params;
      this.source = source;
    }

    Params params() {
      return params;
    }

    Source source() {
      return source;
    }

    @Override
    public String toString() {
      return "Input{" + "params=" + params + ", source=" + source + '}';
    }
  }

  static class Params {

    private final String tag_filter;
    private final String name_filter;
    private final int keep_last_n;

    Params(String tag_filter, String name_filter, int keep_last_n) {
      this.tag_filter = tag_filter;
      this.name_filter = name_filter;
      this.keep_last_n = keep_last_n;
    }

    String tagFilter() {
      return tag_filter;
    }

    String nameFilter() {
      return name_filter;
    }

    int keepLastN() {
      return keep_last_n;
    }

    @Override
    public String toString() {
      return "Params{"
          + "tag_filter='"
          + tag_filter
          + '\''
          + ", name_filter='"
          + name_filter
          + '\''
          + ", keep_last_n="
          + keep_last_n
          + '}';
    }
  }

  static class Source {

    private final String owner;
    private final String repository;
    private final String token;

    Source(String owner, String repository, String token) {
      this.owner = owner;
      this.repository = repository;
      this.token = token;
    }

    String owner() {
      return owner;
    }

    String repository() {
      return repository;
    }

    String token() {
      return token;
    }

    @Override
    public String toString() {
      return "Source{" + "owner='" + owner + '\'' + ", repository='" + repository + '\'' + '}';
    }
  }

  static void sortByPublication(List<Release> releases) {
    releases.sort(
        Comparator.comparing(
            Release::publication, Comparator.nullsFirst(Comparator.naturalOrder())));
  }
}
