FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
EXPOSE 8080
ENV APP_HOME /usr/src/app
WORKDIR $APP_HOME
COPY --from=build /build/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
