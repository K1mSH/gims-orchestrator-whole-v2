FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY jars/api-provider.jar /app/app.jar

EXPOSE 8095

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
