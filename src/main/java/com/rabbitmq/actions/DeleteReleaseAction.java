/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.actions;

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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    String orgRepository = System.getenv("INPUT_REPOSITORY");
    String token = System.getenv("INPUT_TOKEN");
    String tagFilter = System.getenv("INPUT_TAG-FILTER");
    int keepLastN = Integer.valueOf(System.getenv("INPUT_KEEP-LAST-N"));

    Input input =
        new Input(
            new Params(tagFilter, keepLastN),
            new Source(orgRepository.split("/")[0], orgRepository.split("/")[1], token));

    ReleaseAccess access = new GitubRestApiReleaseAccess(input);

    List<Release> releases = access.list();
    List<Release> filteredReleases = filterByTag(releases, input.params().tagFilter());
    if (!filteredReleases.isEmpty()) {
      filteredReleases.sort(Comparator.nullsFirst(Comparator.comparing(Release::publication)));
    }
    List<Release> toDeleteReleases =
        filterForDeletion(filteredReleases, input.params().keepLastN());

    logGreen("Tag filter: " + input.params().tagFilter());

    filteredReleases.forEach(
        r -> {
          if (toDeleteReleases.contains(r)) {
            logYellow("Removing release with tag " + r.tag());
            try {
              access.delete(r);
              access.deleteTag(r);
            } catch (Exception e) {
              logRed("Error while deleting release " + r + ": " + e.getMessage());
            }
          } else {
            log(" Keeping release with tag " + r.tag());
          }
        });
  }

  private static void checkParameter(String env, String arg) {
    if (System.getenv(env) == null) {
      throw new IllegalArgumentException("Parameter " + arg + " must be set");
    }
  }

  private static void testSequence() {
    Consumer<String> display = m -> logGreen(m);
    String message;
    int exitCode = 0;
    try {
      String testUri = "https://www.wikipedia.org/";
      logYellow("Starting test sequence, trying to reach " + testUri);
      HttpRequest request = HttpRequest.newBuilder().uri(new URI(testUri)).GET().build();
      HttpResponse<Void> response =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(60))
              .build()
              .send(request, BodyHandlers.discarding());
      int statusClass = response.statusCode() - response.statusCode() % 100;
      message = "Response code is " + response.statusCode();
      if (statusClass != 200) {
        display = m -> logRed(m);
        exitCode = 1;
      }
    } catch (Exception e) {
      message = "Error during test sequence: " + e.getMessage();
      display = m -> logRed(m);
      exitCode = 1;
    }
    display.accept(message);
    System.exit(exitCode);
  }

  static void logGreen(String message) {
    log("\u001B[32m" + message + "\u001B[0m");
  }

  static void logYellow(String message) {
    log("\u001B[33m" + message + "\u001B[0m");
  }

  static void logRed(String message) {
    log("\u001B[31m" + message + "\u001B[0m");
  }

  static void log(String message, Object... args) {
    System.out.println(String.format(message, args));
  }

  static void out(String message) {
    System.out.println(message);
  }

  static List<Release> filterByTag(List<Release> releases, String tagRegex) {
    Pattern pattern = Pattern.compile(tagRegex);
    return releases.stream()
        .filter(r -> pattern.matcher(r.tag()).matches())
        .collect(Collectors.toList());
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
      releases.sort(Comparator.nullsFirst(Comparator.comparing(Release::publication)));
      return releases.subList(0, releases.size() - keepLastN);
    }
  }

  interface ReleaseAccess {

    List<Release> list();

    void delete(Release release);

    void deleteTag(Release release);
  }

  static class GitubRestApiReleaseAccess implements ReleaseAccess {

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
        // TODO check response code
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void deleteTag(Release release) {
      // https://api.github.com/repos/rabbitmq/rabbitmq-server-binaries-dev/git/refs/tags/v3.9.0-alpha-test.1
      HttpRequest request = requestBuilder("/git/refs/tags/" + release.tag()).DELETE().build();
      try {
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        // TODO check response code
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
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

    Release() {}

    Release(long id, String tag) {
      this.id = id;
      this.tag_name = tag;
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
    private final int keep_last_n;

    Params(String tag_filter, int keep_last_n) {
      this.tag_filter = tag_filter;
      this.keep_last_n = keep_last_n;
    }

    String tagFilter() {
      return tag_filter;
    }

    int keepLastN() {
      return keep_last_n;
    }

    @Override
    public String toString() {
      return "Params{" + "tag_filter='" + tag_filter + '\'' + ", keep_last_n=" + keep_last_n + '}';
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
}
