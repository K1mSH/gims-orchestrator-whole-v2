# Build 단계
FROM gradle:8.11.1-jdk17 AS builder

WORKDIR /build

ARG GIT_TAG
ENV APP_VERSION=$GIT_TAG

COPY . /build/

RUN chmod +x gradlew

RUN echo "빌드할 버전: $APP_VERSION"
RUN ./gradlew clean build --no-daemon -x test
RUN rm -rf /build/.gradle

# curl이 포함된 컨테이너에서 curl 바이너리를 가져오기
FROM curlimages/curl:8.12.1 AS curl_builder

# Runtime 단계
FROM openjdk:17-jdk-slim

WORKDIR /app

ARG GIT_TAG
ENV APP_VERSION=$GIT_TAG

COPY --from=curl_builder /usr/bin/curl /usr/bin/curl
COPY --from=curl_builder /usr/lib /usr/lib
COPY --from=curl_builder /lib /lib

COPY --from=builder /build/build/libs/*.jar infolink-api-collector.jar

ENTRYPOINT ["java", "-jar", "infolink-api-collector.jar"]
