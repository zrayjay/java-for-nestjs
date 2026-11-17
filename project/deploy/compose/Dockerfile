FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY examples/java-basics examples/java-basics
COPY examples/spring-boot-examples examples/spring-boot-examples
COPY examples/spring-web-lab examples/spring-web-lab
COPY examples/data-access-lab examples/data-access-lab
COPY examples/security-lab examples/security-lab
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress -pl examples/spring-web-lab -am install -DskipTests \
    && ./mvnw --batch-mode --no-transfer-progress -f examples/data-access-lab/pom.xml install -DskipTests \
    && ./mvnw --batch-mode --no-transfer-progress -f examples/security-lab/pom.xml package -DskipTests

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN groupadd --gid 10001 teaching && useradd --uid 10001 --gid 10001 --no-create-home teaching \
    && mkdir -p /app /keys/target/demo-keys && chown -R 10001:10001 /keys
WORKDIR /app
COPY --from=build /src/examples/security-lab/target/security-lab-0.1.0-SNAPSHOT-exec.jar /app/app.jar
USER 10001:10001
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
