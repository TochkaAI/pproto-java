/*
 * MIT License
 *
 * Copyright (c) 2020 Alexander Shilov (ashlanderr) <aleksandr.schilow2012@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ai.tochka.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
        public Long tag;
    }

    public interface JavaClient {
        @Command(id = "java-command")
        AnswerContent javaCommand(@Tag Long tag, CommandContent command);

        @Event(id = "java-event")
        void javaEvent(@Tag long tag, CommandContent event);
    }

    public static class JavaServer {
        public CompletableFuture<CommandContent> received = new CompletableFuture<>();

        @CommandHandler(id = "java-command")
        public AnswerContent javaCommand(@Tag Long tag, CommandContent command) {
            Assert.assertEquals(100, command.bar);
            AnswerContent answer = new AnswerContent();
            answer.foo = command.foo;
            answer.qux = 456;
            answer.tag = tag;
            return answer;
        }

        @EventHandler(id = "java-event")
        public void javaEvent(@Tag long tag, CommandContent event) {
            Assert.assertEquals(321, tag);
            received.complete(event);
        }
    }

    private MessageRegistry registry;

    private ServerChannel listenChan;
    private Channel serverChan;
    private ClientChannel clientChan;

    private Thread listenThread;
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;

    @Before
    public void beforeTest() throws ExecutionException, InterruptedException, TimeoutException {
        serverExecutor = Executors.newSingleThreadExecutor();
        clientExecutor = Executors.newSingleThreadExecutor();

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        registry = new MessageRegistry(mapper, false);

        listenChan = new ServerChannel(
                8000,
                registry,
                serverExecutor
        );
        CompletableFuture<Void> serverFuture = new CompletableFuture<>();

        listenThread = new Thread(() -> listenChan.listen(channel -> {
            serverChan = channel;
            serverFuture.complete(null);
        }));
        listenThread.start();

        clientChan = new ClientChannel(
                "127.0.0.1",
                8000,
                registry,
                clientExecutor
        );
        CompletableFuture<Void> clientFuture = new CompletableFuture<>();
        clientChan.onConnect(socket -> {
            clientFuture.complete(null);
        });

        serverFuture.get(60, TimeUnit.SECONDS);
        clientFuture.get(60, TimeUnit.SECONDS);
    }

    @After
    public void afterTest() throws IOException, InterruptedException {
        clientChan.close();
        serverChan.close();
        listenChan.close();
        listenThread.join();
        clientExecutor.shutdown();
        serverExecutor.shutdown();
    }

    @Test
    public void testJavaApi() throws InterruptedException, ExecutionException, TimeoutException {
        JavaClient client = clientChan.service(JavaClient.class);

        JavaServer server = new JavaServer();
        serverChan.handler(server, JavaServer.class);

        CommandContent command = new CommandContent();
        command.foo = "test string";
        command.bar = 100;
        AnswerContent answer = client.javaCommand(123L, command);
        Assert.assertEquals("test string", answer.foo);
        Assert.assertEquals(456, answer.qux);
        Assert.assertEquals(123, answer.tag.longValue());

        CommandContent event = new CommandContent();
        event.foo = "event string";
        event.bar = 200;
        client.javaEvent(321, event);
        CommandContent received = server.received.get(60, TimeUnit.SECONDS);
        Assert.assertEquals("event string", received.foo);
        Assert.assertEquals(200, received.bar);
    }

    @Test
    public void testNullTag() {
        JavaClient client = clientChan.service(JavaClient.class);

        JavaServer server = new JavaServer();
        serverChan.handler(server, JavaServer.class);

        CommandContent command = new CommandContent();
        command.foo = "test string";
        command.bar = 100;
        AnswerContent answer = client.javaCommand(null, command);
        Assert.assertEquals("test string", answer.foo);
        Assert.assertEquals(456, answer.qux);
        Assert.assertNull(answer.tag);
    }

    @Test
    public void testJavaSerialization() throws JsonProcessingException {
        registry.registerContentType(MessageType.COMMAND, "serialization", CommandContent.class);

        CommandContent command = new CommandContent();
        command.foo = "test string";
        command.bar = 100;
        command.nested = new CommandContent();
        command.nested.date = OffsetDateTime.ofInstant(Instant.ofEpochSecond(100), ZoneId.of("UTC"));
        command.testEnum = TestEnum.FIRST;
        command.nested.testEnum = TestEnum.SECOND;

        String str = registry.getObjectMapper().writeValueAsString(command);

        Assert.assertEquals(
                "{\"foo\":\"test string\",\"bar\":100,\"date\":null,\"testEnum\":0,\"nested\":{\"foo\":null,\"bar\":0,\"date\":100000,\"testEnum\":1,\"nested\":null}}",
                str
        );
    }
}
