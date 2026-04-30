FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY jars/proxy-internal.jar /app/app.jar

EXPOSE 8093

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
