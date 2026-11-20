FROM node:22.20.0-bookworm-slim AS web
WORKDIR /src
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY project/apps/admin-web/package.json project/apps/admin-web/package.json
RUN corepack enable && pnpm --filter @java-for-nestjs/admin-web install --frozen-lockfile
COPY project/apps/admin-web project/apps/admin-web
RUN pnpm --filter @java-for-nestjs/admin-web build

FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY project/pom.xml project/pom.xml
COPY project/java-modules project/java-modules
COPY project/apps/admin-server project/apps/admin-server
COPY --from=web /src/project/apps/admin-web/dist/ project/apps/admin-server/src/main/resources/static/
COPY examples/security-lab/src/main/java/dev/javafornestjs/security/KeyTool.java /tmp/KeyTool.java
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress -f project/pom.xml package -DskipTests \
    && javac -d /src/key-tool /tmp/KeyTool.java

FROM eclipse-temurin:21.0.13_9-jre-noble
ARG CAPSTONE_REVISION=local
LABEL org.opencontainers.image.revision=$CAPSTONE_REVISION
RUN groupadd --gid 10001 capstone && useradd --uid 10001 --gid 10001 --no-create-home capstone \
    && install -d -m 0700 -o capstone -g capstone /keys /keys/target /keys/target/demo-keys
WORKDIR /app
COPY --from=build /src/project/apps/admin-server/target/admin-server-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /src/key-tool /app/key-tool
USER 10001:10001
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
