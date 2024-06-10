# Этап 1: Сборка приложения с помощью Node.js
FROM node:lts as build

# Рабочая директория внутри контейнера
WORKDIR /usr/src/app

# Копируем package.json и package-lock.json (если есть)
COPY package*.json ./

#RUN npm install -g npm@10.8.1

# Устанавливаем зависимости
RUN npm install --force

# Копируем остальные файлы проекта
COPY . .

# Выполняем сборку проекта
RUN npm run build --prod

# Этап 2: Запуск приложения с помощью Nginx
FROM nginx:alpine

# Копируем Nginx конфигурацию
#COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN rm -rf /usr/share/nginx/html/*
# Копируем собранные файлы из предыдущего этапа
COPY --from=build /usr/src/app/dist/frontend /usr/share/nginx/html

# Открываем порт 80
EXPOSE 80

# Запускаем Nginx
CMD ["nginx", "-g", "daemon off;"]
