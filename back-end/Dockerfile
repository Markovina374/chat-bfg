# Этап 1: Сборка приложения с помощью Gradle
FROM gradle:7.4-jdk17 AS builder

# Устанавливаем Redis
RUN apt-get update && apt-get install -y redis

# Копируем файлы сборки и исходный код в контейнер
COPY --chown=gradle:gradle . /home/gradle/project

# Устанавливаем рабочую директорию
WORKDIR /home/gradle/project

# Выполняем сборку проекта с помощью Gradle
RUN gradle fatJar --no-daemon

# Проверка содержимого директории сборки (для отладки)
RUN ls -la /home/gradle/project/build/libs/

# Этап 2: Запуск приложения
FROM openjdk:17-jdk-slim

# Устанавливаем рабочую директорию в контейнере
WORKDIR /app

# Копируем собранный JAR-файл из предыдущего этапа
COPY --from=builder /home/gradle/project/build/libs/*.jar /app/app.jar

# Открываем порт, который будет использовать ваше приложение
EXPOSE 8090

# Экспонируем порт Redis
EXPOSE 6379

# Команда для запуска вашего приложения
CMD service redis-server start & java -jar app.jar
