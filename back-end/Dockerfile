
FROM gradle:7.4-jdk17 AS builder

RUN apt-get update && apt-get --force-yes -y install redis

COPY --chown=gradle:gradle . /home/gradle/project

WORKDIR /home/gradle/project

RUN gradle fatJar --no-daemon

RUN ls -la /home/gradle/project/build/libs/

FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=builder /home/gradle/project/build/libs/*.jar /app/app.jar

EXPOSE 8090

EXPOSE 6379

CMD apt-get update && apt-get --force-yes -y install redis && service redis-server start && java -jar app.jar
