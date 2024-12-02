# Overview

This is a GitHub Action to delete old releases.

# Usage

```yaml
- name: Delete old releases
  uses: docker://pivotalrabbitmq/delete-release-action:latest
  with:
    repository: rabbitmq/rabbitmq-java-tools-binaries-dev
    token: ${{ secrets.CI_GITHUB_TOKEN }}
    tag-filter: '^v-stream-perf-test-0.[0-9]+.0-SNAPSHOT-[0-9]{8}-[0-9]{6}$'
    keep-last-n: 2
```

The filtering can be used on the tag name (`tag-filter`) or on the release name (`name-filter`).

# License and Copyright

(c) 2022-2024 Broadcom. All Rights Reserved.
The term Broadcom refers to Broadcom Inc. and/or its subsidiaries.

This package, the Concourse GitHub Release Delete Resource, is licensed
under the Mozilla Public License 2.0 ("MPL").

See [LICENSE](./LICENSE).
