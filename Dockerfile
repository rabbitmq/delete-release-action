FROM ubuntu:24.04 AS builder

RUN set -eux; \
	apt-get update; \
	apt-get -y upgrade; \
	apt-get install --yes --no-install-recommends \
		ca-certificates \
		wget \
		jq

ARG JAVA_VERSION="21"

RUN ARCH="x86"; BUNDLE="jdk"; \
    wget "https://api.azul.com/zulu/download/community/v1.0/bundles/latest/?java_version=$JAVA_VERSION&ext=tar.gz&os=linux&arch=$ARCH&hw_bitness=64&release_status=ga&bundle_type=$BUNDLE" -O jdk-info.json

RUN wget --progress=bar:force:noscroll -O "jdk.tar.gz" $(cat jdk-info.json | jq --raw-output .url)
RUN echo "$(cat jdk-info.json | jq --raw-output .sha256_hash) *jdk.tar.gz" | sha256sum --check --strict -

ENV JAVA_HOME="/usr/lib/jdk-$JAVA_VERSION"

RUN set -eux; \
    mkdir $JAVA_HOME && \
    tar --extract  --file jdk.tar.gz --directory "$JAVA_HOME" --strip-components 1; \
	  $JAVA_HOME/bin/jlink --compress=zip-6 --output /jre --add-modules java.base,jdk.crypto.cryptoki,java.net.http; \
	  /jre/bin/java -version \
    ; \
    mkdir -p /app

RUN mkdir -p /project/src /project/.mvn
COPY src /project/src
COPY .mvn /project/.mvn
COPY pom.xml /project
COPY mvnw /project

WORKDIR /project
RUN set -eux; \
    ./mvnw package -Dmaven.test.skip --no-transfer-progress

FROM ubuntu:24.04

RUN set -eux; \
	\
	apt-get update; \
	apt-get -y upgrade; \
	apt-get install --yes --no-install-recommends \
		ca-certificates \
  ; \
	rm -rf /var/lib/apt/lists/*

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk/jre
RUN mkdir -p $JAVA_HOME
COPY --from=builder /jre $JAVA_HOME/
RUN ln -svT $JAVA_HOME/bin/java /usr/local/bin/java

RUN mkdir -p /app

COPY --from=builder /project/target/delete-release-action.jar /app
RUN set -eux; \
    java -jar /app/delete-release-action.jar test

RUN groupadd --gid 1042 github
RUN useradd --uid 1042 --gid github --comment "github user" github

USER github:github

ENTRYPOINT ["java", "-jar", "/app/delete-release-action.jar"]
