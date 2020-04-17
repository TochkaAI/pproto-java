Java-реализация RPC-протокола компании [Точка Зрения](https://tochka.ai/).

# Установка

gradle:

```groovy
repositories {
    jcenter()
}

dependencies {
    implementation 'ai.tochka:pproto-java:$version'
}
```

maven:

```xml
<repository>
    <snapshots>
        <enabled>false</enabled>
    </snapshots>
    <id>central</id>
    <name>bintray</name>
    <url>http://jcenter.bintray.com</url>
</repository>

<dependency>
    <groupId>ai.tochka</groupId>
    <artifactId>pproto-java</artifactId>
    <version>$version</version>
</dependency>
```

# Документация

## Инициализация

Для начала работы с библиотекой нужно создать объект `ProtocolConnection`.

```java
// Свойства подключения.
// Если ничего не указывать, будут использоваться значения по умолчанию.
ProtocolProperties properties = new ProtocolProperties();

// Фабрика сокетов, которые будут использоваться для установки соединения.
// Сейчас доступна только TcpClientProtocolSocketFactory.
ProtocolSocketFactory socketFactory = new TcpClientProtocolSocketFactory("127.0.0.1", 62062);

// Пул потоков, в котором будут обрабатываться входящие запросы.
// Кроме этого библиотека создаёт отдельный поток для ожидания сообщений от сервера.
ExecutorService executor = Executors.newSingleThreadExecutor();

// Jackson Object Mapper для сериализации запросов и ответов в JSON.
ObjectMapper mapper = new ObjectMapper();

// Создание соединения.
// При разрывах связи будет выполнено автоматическое переподключение, используя socketFactory.
ProtocolConnection connection = ProtocolConnection.create(
        properties,
        socketFactory,
        executor,
        mapper
);

// Запуск клиента. Метод возвращает future, который завершится после установки соединения с сревером.
// future никогда не завершится с ошибкой, вместо этого connection будет постоянно пытаться переподключаться к серверу.
CompletableFuture<void> future = connection.start();
future.get();
```

`ProtocolConnection` содержит методы для отправки и получения сообщений. 
Скорее всего вам не нужно будет использовать его напрямую. 
Вместо этого используйте высокоуровневые обёртки `ProtocolServiceFactory` и `ProtocolListener`.

## Клиент

Чтобы выполнять запросы к серверу, нужно описать интерфейс и создать его реализацию с помощью фабрики. 
Такой интерфейс называется сервисом в терминах библиотеки. Каждый метод сервиса описывает запрос к серверу. 
Возвращаемое значение метода - ответ сервера.

Для примера предположим, что мы обращаемся к серверу, который отвечает на запрос `hello(name)` строкой `"Hello, $name!"`.

```java
// Класс описывающий запрос к серверу.
// Этот класс должен поддерживать сериализацию в JSON.
class ExampleCommand {
    public String name;
}

// Класс описывающий ответ от сервера.
// Этот класс должен поддерживать сериализацию в JSON.
class ExampleAnswer {
    public String greeting;
}

interface ExampleService {
    // Вызов этого метода отправит запрос на сервер с типом команды [type].
    // Ответ от сервера вернётся как результат выполнения метода.
    // Обычно [type] - сгенерированный UUID.
    @Command(type = "38fc19b9-b8af-4693-a7c1-12bd6e08186a")
    ExampleAnswer hello(ExampleCommand command);
}

// Создание вспомогательного класса, который создаёт реализации интерфейсов.
// Объект serviceFactory можно переиспользовать во всём приложении.
ProtocolServiceFactory serviceFactory = new ProtocolServiceFactory(connection);

// Создание реализации интерфейса.
// При вызове этого метода всегда создаётся новый объект.
ExampleService service = serviceFactory.create(ExampleService.class);

// Выполнение запроса к серверу.
ExampleCommand command = new ExampleCommand();
command.name = "World";
// answer.greeting = "Hello, World!"
ExampleAnswer answer = service.hello(command);
```

Методы сервисов могут содержать от 0 до 2 аргументов:

- Без аргументов - будет отправлено пустое тело запроса, `null`.
- Аргумент без аннотаций - интерпретируется как тело запроса.
- Аргумент с аннотацией `@Tag` - дополнительный тег передаваемый все тела запроса. Может использоваться для передачи аутентификации или другой информации, проходящей через много запросов. Этот аргумент должен быть типа `long`.

Вызов метода может завершиться 3 способами:
- При успешном завершении метод вернёт ответ от сервера. 
- При возникновении ошибки бизнес-логики метод бросит исключение типа `ProtocolAnswerException` с описанием ошибки.
- При возникновении внутренней ошибки метод бросит исключение типа `ProtocolException`. Это может произойти, когда разорвалось соединение с сервером или не удалось распарсить сообщение.

Обращение к методам сервиса потокобезопасно. Если один поток отправляет запрос, то другие будут ждать в очереди.

## Сервер

Взаимодействие по протоколу симметрично, роли сервера и клиента существуют только в момент установки соединения. 
Сервер слушает TCP порт, а клиент подключается к серверу, открывая клиентский TCP сокет. 
После этого обе стороны могут посылать друг другу запросы. 

Для обработки входящих запросов нужно реализовать их обработчик в виде класса и зарегистрировать его в `connection`. 
Регистрация выполняется вспомогательным классом `ProtocolListener`.
Для примера реализуем сервер из предыдущего раздела.

```java
// Класс описывающий запрос к серверу.
// Этот класс должен поддерживать сериализацию в JSON.
class ExampleCommand {
    public String name;
}

// Класс описывающий ответ от сервера.
// Этот класс должен поддерживать сериализацию в JSON.
class ExampleAnswer {
    public String greeting;
}

// Класс обработчик входящих запросов
class GreetingHandler {
    // Метод обрабатывающий запрос `hello`.
    // Этот метод будет вызван, когда на сервер придёт соответствующий запрос.
    // Результат метода - ответ от сервера.
    @CommandHandler(type = "38fc19b9-b8af-4693-a7c1-12bd6e08186a")
    public ExampleAnswer hello(ExampleCommand command) {
        ExampleAnswer answer = new ExampleAnswer();
        answer.greeting = "Hello, " + command.name + "!";
        return answer;
    }
}

// Создание вспомогательного класса, который регистрирует обработчики запросов.
// Объект listener можно переиспользовать во всём приложении.
ProtocolListener listener = new ProtocolListener(connection);

// Создание обработчика
GreetingHandler handler = new GreetingHandler();

// Регистрация обработчика
listener.connect(handler, GreetingHandler.class);
```

Если выполнение обработчика запроса завершилось с ошибкой, то можно бросить исключение `ProtocolAnswerException`,
тогда клиент получит такое же исключение и сможет обработать эту ситуацию. Если произошла непредвиденная ошибка,
то библиотека бросит искючение `ProtocolAnswerException(group = -1, code = "", message = "Unexpected error")`.

Методы обработчиков могут принимать такие же аргументы как и методы сервосов: тело запроса и теги.

Обработчики запросов вызываются в пуле потоков, который был указан в аргументах функции `ProtocolConnection.create`.

## События

Кроме формата запрос-ответ, протокол поддерживает отправку событий - единичных сообщений, на которые не ожидается ответа.

Чтобы реагировать на события, нужно создать класс обработчик и зарегистрировать его в `ProtocolListener`. Для примера возьмём событие регистрации пользователя.

```java
// Класс описывающий событие.
// Этот класс должен поддерживать сериализацию в JSON.
class UserRegisteredEvent {
    String id;
    String email;
    OffsetDateTime timestamp;
}

class RegistrationHandler {
    // Метод обрабатывающий событие.
    // Тип возвращаемого значения всегда должен быть void.
    @EventHandler(type = "d3c95ec0-275a-4015-aaac-d1076507e55c")
    void userRegistered(UserRegisteredEvent event) {
        // обработка регистриации
    }
}

// Создание вспомогательного класса, который регистрирует обработчики событий
ProtocolListener listener = new ProtocolListener(connection);

// Создание обработчика
RegistrationHandler handler = new RegistrationHandler();

// Регистрация обработчика
listener.connect(handler, RegistrationHandler.class);
```

Методы обработчиков событий ведут себя так же как и обработчики запросов, за исключением отсутствия ответа. Они запускаются в том же пуле потоков и могут принимать такие же аргументы.

Чтобы отправить событие, нужно объявить метод в интерфейсе сервиса и создать реализацию с помощью `serviceFactory`.

```java
// Класс описывающий событие.
// Этот класс должен поддерживать сериализацию в JSON.
class UserRegisteredEvent {
    String id;
    String email;
    OffsetDateTime timestamp;
}

interface RegistrationService {
    // Метод посылающий событие.
    // Тип возвращаемого значения всегда должен быть void.
    @Event(type = "d3c95ec0-275a-4015-aaac-d1076507e55c")
    void userRegistered(UserRegisteredEvent event);
}

// Создание вспомогательного класса, который создаёт реализации интерфейсов.
ProtocolServiceFactory serviceFactory = new ProtocolServiceFactory(connection);

// Создание реализации интерфейса.
RegistrationService service = serviceFactory.create(RegistrationService.class);

// Отправка события.
UserRegisteredEvent умуте = new UserRegisteredEvent();
event.id = "user-id";
event.email = "test@example.com";
event.timestamp = OffsetDateTime.now();
// Вызов метода завершиться как только сообщение будет отправлено в сокет. 
service.hello(event);
```

Методы отправки событий работают так же как и методы запросов, за исключением отсутствия ответа.

## Завершение работы

Чтобы завершить работу библиотеки необходимо закрыть соединение и остановить пул потоков. В этот момент будет разорвано соединение с сервером и завершатся все ожидающие запросы.

```java
connection.close();
executor.shutdown();
```
