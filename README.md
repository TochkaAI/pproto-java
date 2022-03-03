Java реализация RPC протокола [PProto](https://github.com/hkarel/PProtoCpp).

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

## Терминология

- Channel - объект, представляющий клиентское или серверное соединение. 
Channel может переживать переподключения сокетов. 
С помощью Channel можно создавать сервисы или обработчики запросов. 

- Service - интерфейс, методы которого соответствуют вызовам команд на сервере.

- Handler - класс, методы которого будут вызваны, когда на сервер придёт запрос от клиента.

## Клиент

Чтобы подключиться к серверу нужно создать объект `ClientChannel`.

```java
// Пул потоков, в котором будут обрабатываться входящие запросы.
// Кроме этого библиотека создаёт отдельный поток для ожидания сообщений от сервера.
ExecutorService executor = Executors.newSingleThreadExecutor();

// Jackson Object Mapper для сериализации запросов и ответов в JSON.
ObjectMapper mapper = new ObjectMapper();

// Реестр зарегистрированных типов данных. Его можно переиспользовать между разными каналами.
registry = MessageRegistry(mapper);

// Создание соединения на адрес 127.0.0.1:8000
// При разрывах связи будет выполнено автоматическое переподключение.
ClientChannel channel = new ClientChannel(
    "127.0.0.1", 
    8000, 
    registry, 
    executor
);
```

Чтобы выполнять запросы к серверу, нужно описать интерфейс и создать его реализацию с помощью метода `channel.service`. 
Каждый метод интерфейса описывает запрос к серверу. 
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
    // Вызов этого метода отправит запрос на сервер с типом команды [id].
    // Ответ от сервера вернётся как результат выполнения метода.
    // Обычно [id] - сгенерированный UUID.
    @Command(id = "38fc19b9-b8af-4693-a7c1-12bd6e08186a")
    ExampleAnswer hello(ExampleCommand command);
}

// Создание реализации интерфейса.
// При вызове этого метода всегда создаётся новый объект.
ExampleService service = channel.create(ExampleService.class);

// Выполнение запроса к серверу.
ExampleCommand command = new ExampleCommand();
command.name = "World";

// answer.greeting = "Hello, World!"
ExampleAnswer answer = service.hello(command);
```

Методы сервисов могут принимать несколько аргументов:

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

Для ожидания соединений от клиента нужно создать объект `ServerChannel` и вызвать метод `listen`.

```java
// Пул потоков, в котором будут обрабатываться входящие запросы.
ExecutorService executor = Executors.newSingleThreadExecutor();

// Jackson Object Mapper для сериализации запросов и ответов в JSON.
ObjectMapper mapper = new ObjectMapper();

// Реестр зарегистрированных типов данных. Его можно переиспользовать между разными каналами.
registry = MessageRegistry(mapper);

// Создание серверного канала, который слушает запросы от клиентов на порту 8000.
ServerChannel serverChannel = new ServerChannel(8000, registry, executor);

// Ожидание подключений от клиентов.
serverChannel.listen(clientChannel -> {
    // Эта лямбда вызывается в отдельном потоке для каждого соединения.
    // Здесь нужно зарегистрировать обработчики запросов.
});
```

Для обработки входящих запросов нужно реализовать их обработчик в виде класса и зарегистрировать его в `clientChannel`. 
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
    @CommandHandler(id = "38fc19b9-b8af-4693-a7c1-12bd6e08186a")
    public ExampleAnswer hello(ExampleCommand command) {
        ExampleAnswer answer = new ExampleAnswer();
        answer.greeting = "Hello, " + command.name + "!";
        return answer;
    }
}

// Создание обработчика
GreetingHandler handler = new GreetingHandler();

// Регистрация обработчика внутри лямбды метода listen
clientChannel.handler(handler, GreetingHandler.class);
```

Если выполнение обработчика запроса завершилось с ошибкой, то можно бросить исключение `ProtocolAnswerException`,
тогда клиент получит такое же исключение и сможет обработать эту ситуацию. Если произошла непредвиденная ошибка,
то библиотека бросит искючение `ProtocolAnswerException(group = -1, code = "", message = "Unexpected error")`.

Методы обработчиков могут принимать такие же аргументы как и методы сервосов: тело запроса и теги.
Кроме этого, в аргументе с аннотацией `@MessageId` будет находится идентификатор сообщения.

Обработчики запросов вызываются в пуле потоков, который был указан в аргументах конструктора `ServerChannel`.

## События

Кроме формата запрос-ответ, протокол поддерживает отправку событий - единичных сообщений, на которые не ожидается ответа.

Чтобы реагировать на события, нужно создать класс обработчик и зарегистрировать его в `Channel`. Для примера возьмём событие регистрации пользователя.

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
    @EventHandler(id = "d3c95ec0-275a-4015-aaac-d1076507e55c")
    void userRegistered(UserRegisteredEvent event) {
        // обработка регистриации
    }
}

// Создание обработчика
RegistrationHandler handler = new RegistrationHandler();

// Регистрация обработчика
channel.connect(handler, RegistrationHandler.class);
```

Методы обработчиков событий ведут себя так же как и обработчики запросов, за исключением отсутствия ответа. Они запускаются в том же пуле потоков и могут принимать такие же аргументы.

Чтобы отправить событие, нужно объявить метод в интерфейсе сервиса и создать реализацию с помощью метода `channel.service`.

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
    @Event(id = "d3c95ec0-275a-4015-aaac-d1076507e55c")
    void userRegistered(UserRegisteredEvent event);
}

// Создание реализации интерфейса.
RegistrationService service = channel.create(RegistrationService.class);

// Отправка события.
UserRegisteredEvent event = new UserRegisteredEvent();
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
channel.close();
executor.shutdown();
```
