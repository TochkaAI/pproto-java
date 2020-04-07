package ai.tochka.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;

public class JavaApiTest {
    public enum TestEnum {
        FIRST,
        SECOND,
    }

    public static class CommandContent {
        public String foo;
        public int bar;
        public OffsetDateTime date;
        public TestEnum testEnum;
        public CommandContent nested;
    }

    public static class AnswerContent {
        public String foo;
        public long qux;
    }

    public interface JavaClient {
        @Command(type = "java-command")
        AnswerContent javaCommand(@Tag long tag, CommandContent command);

        @Event(type = "java-event")
        void javaEvent(@Tag long tag, CommandContent event);
    }

    public static class JavaServer {
        public CompletableFuture<CommandContent> received = new CompletableFuture<>();

        @CommandHandler(type = "java-command")
        public AnswerContent javaCommand(@Tag long tag, CommandContent command) {
            Assert.assertEquals(123, tag);
            Assert.assertEquals(100, command.bar);
            AnswerContent answer = new AnswerContent();
            answer.foo = command.foo;
            answer.qux = 456;
            return answer;
        }

        @EventHandler(type = "java-event")
        public void javaEvent(@Tag long tag, CommandContent event) {
            Assert.assertEquals(321, tag);
            received.complete(event);
        }
    }

    private ArrayList<Closeable> dispose = new ArrayList<>();
    private ProtocolConnection serverConn;
    private ProtocolConnection clientConn;

    @Before
    public void beforeTest() throws ExecutionException, InterruptedException {
        ExecutorService executor1 = Executors.newSingleThreadExecutor();
        dispose.add(executor1::shutdown);

        ExecutorService executor2 = Executors.newSingleThreadExecutor();
        dispose.add(executor2::shutdown);

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(CustomPropertyNamingStrategy.INSTANCE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        serverConn = ProtocolConnection.create(
                new ProtocolProperties(),
                new TcpServerProtocolSocketFactory(8000),
                executor1,
                mapper
        );
        dispose.add(serverConn);
        CompletableFuture<Void> serverFuture = serverConn.start();

        clientConn = ProtocolConnection.create(
                new ProtocolProperties(),
                new TcpClientProtocolSocketFactory("127.0.0.1", 8000),
                executor2,
                mapper
        );
        dispose.add(clientConn);
        clientConn.start().get();
        serverFuture.get();
    }

    @After
    public void afterTest() throws IOException {
        ArrayList<Closeable> list = new ArrayList<>(dispose);
        Collections.reverse(list);
        for (Closeable closeable : list) {
            closeable.close();
        }
    }

    @Test
    public void testJavaApi() throws InterruptedException, ExecutionException, TimeoutException {
        ProtocolServiceFactory serviceFactory = new ProtocolServiceFactory(clientConn);
        JavaClient client = serviceFactory.create(JavaClient.class);

        ProtocolListener listener = new ProtocolListener(serverConn);
        JavaServer server = new JavaServer();
        listener.connect(server, JavaServer.class);

        CommandContent command = new CommandContent();
        command.foo = "test string";
        command.bar = 100;
        AnswerContent answer = client.javaCommand(123, command);
        Assert.assertEquals("test string", answer.foo);
        Assert.assertEquals(456, answer.qux);

        CommandContent event = new CommandContent();
        event.foo = "event string";
        event.bar = 200;
        client.javaEvent(321, event);
        CommandContent received = server.received.get(60, TimeUnit.SECONDS);
        Assert.assertEquals("event string", received.foo);
        Assert.assertEquals(200, received.bar);
    }

    @Test
    public void testJavaSerialization() throws JsonProcessingException {
        clientConn.registerContentType(MessageType.COMMAND, "serialization", CommandContent.class);

        CommandContent command = new CommandContent();
        command.foo = "test string";
        command.bar = 100;
        command.nested = new CommandContent();
        command.nested.date = OffsetDateTime.ofInstant(Instant.ofEpochSecond(100), ZoneId.of("UTC"));
        command.testEnum = TestEnum.FIRST;
        command.nested.testEnum = TestEnum.SECOND;

        String str = clientConn.getObjectMapper().writeValueAsString(command);

        Assert.assertEquals(
                "{\"foo\":\"test string\",\"bar\":100,\"date\":null,\"testEnum\":0,\"nested\":{\"foo\":null,\"bar\":0,\"date\":100000,\"testEnum\":1,\"nested\":null}}",
                str
        );
    }
}
