Тестовое задание(Бэк), сделано в минималистичном стиле с использовнием фреймворка Vert.x. 
 ![image](https://github.com/Markovina374/chat-bfg/assets/70856941/beb7d996-7283-4707-bd23-a8df7654316a)
 
 
 
 Это фреймворк для реактивного программирования который реализует паттерн реактор. 
 Всё приложение состоит из сущностей типа Verticle, которые общаются между собой через EventBus. 
 EventBus это шина сообщений, которую нам так любезно предоставляют создатели фреймворка.
 Verticle могут исполняться под одной Jvm так и под несколькими, при этом Verticle становится самодостаточным микросервисом и может спокойно горизонтально масштабироваться.
 Фронтовая часть выполнена на Angular. 
 Клиент и сервер общаются между собой посредством Web-Socket.
 
 
 
 На серверной части представлено только 5 основных классов: 


1.MainVerticle - это точка входа в приложение, которое "деплоит" другие вертиклы;


2.WebSocketVerticle - это вертикл которое выполняет роль маршрутизатора и ответа пользователю, а также для контроля подключений;


3.JwtAuthVerticle - этот вертикл отвечает за Аутентификацию;


4.RedisVerticle - данный вертикл общается с базой данных;


5.UserStatusVerticle - этот вертикл отвечает за актуализацию online Пользователей;

Все они общаются между собой Json ами через Event Bus, и отдают друг другу ответы очень быстро, поэтому маппинг в объекты и обратно делать не стал.

Как проверить приложение: 

1. Надо сделать клон проекта с гитхаба,
2. Проверить что порты 4200, 8090, 6379 не заняты на локальной машине
3. Перейти в корень проекта через терминал и выполнить команду       docker-compose up
4. Подождать пока скачается интернет), возможно потребуется vpn для подключения к docker-hub
5. После того как всё завершиться перейти по адресу http://localhost:4200
6. Для тестирования нескольких пользователей нужно воспользоваться вкладкой инкогнито или другим браузером





![image](https://github.com/Markovina374/chat-bfg/assets/70856941/30d6f1e3-8418-4c3a-949d-4c8b454d22c6)

В левой части будут высвечиваться Онлайн пользователи.

P.S. Angular щупал второй раз в жизни, Vert.x первый) Когда писал было очень интересно, много чего бы сделал по другому, некоторые вещи, например конфиг файлы и тесты не успел написать. 
                                                           
        
    
 
