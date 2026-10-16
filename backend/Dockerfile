# Image of one Java service (DEP-01), shared by the 4 services. Build context: backend/.
#
#   docker build -f backend/Dockerfile --build-arg SERVICE=listing-service \
#     --build-arg EXTRA_PACKAGES=libvips-tools --build-arg SERVICE_PORT=8082 \
#     -t soukly-listing-service:local backend
#
# Nothing specific to an environment goes in the image: configuration comes from environment
# variables at startup, so the image tested in dev is the one promoted to prod.

# ---- Build: compile the service and split its jar into layers ----
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87 AS build

# Maven module to package: gateway, user-service, listing-service or social-publisher.
ARG SERVICE
# Optional Maven Central mirror (for example behind a corporate proxy); empty = Maven Central.
ARG MAVEN_MIRROR_URL=""

WORKDIR /workspace
COPY . .

# Tests are not run here: the CI (ci.yml) runs them on every PR.
# ${settings} is split on purpose (empty or two arguments).
# hadolint ignore=SC2086
RUN --mount=type=cache,target=/root/.m2,id=soukly-maven \
    set -eu; \
    if [ -z "${SERVICE}" ] || [ ! -f "${SERVICE}/pom.xml" ]; then \
      echo "Build argument SERVICE must name a Maven module of backend/ (got '${SERVICE}')" >&2; exit 1; \
    fi; \
    settings=""; \
    if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      export MVNW_REPOURL="${MAVEN_MIRROR_URL}"; \
      printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' \
        "${MAVEN_MIRROR_URL}" > /tmp/maven-settings.xml; \
      settings="-s /tmp/maven-settings.xml"; \
    fi; \
    ./mvnw -B -ntp ${settings} -pl "${SERVICE}" -am package -DskipTests; \
    cp "${SERVICE}/target/${SERVICE}-"*.jar application.jar; \
    # Spring Boot 4.0 layout: application.jar plus lib/, split into dependencies, spring-boot-loader,
    # snapshot-dependencies and application layers.
    java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Runtime: JRE only, non-root, layers ordered from the least to the most often changed ----
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c

# Space-separated apt packages needed at runtime (libvips-tools for listing-service and social-publisher).
ARG EXTRA_PACKAGES=""
# Port the service listens on (8080 to 8083), documented by EXPOSE.
ARG SERVICE_PORT=8080

# EXTRA_PACKAGES is split on purpose; versions come from the pinned base image's Ubuntu release.
# hadolint ignore=DL3008,SC2086
RUN set -eu; \
    if [ -n "${EXTRA_PACKAGES}" ]; then \
      apt-get update; \
        apt-get install -y --no-install-recommends ${EXTRA_PACKAGES}; \
      rm -rf /var/lib/apt/lists/*; \
    fi; \
    groupadd --system --gid 10001 soukly; \
    useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin soukly

WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "application.jar"]
