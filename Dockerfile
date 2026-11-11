FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build --chown=app:app /work/target/nexo-1.0.0.jar /app/nexo.jar
USER app
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/nexo.jar"]
