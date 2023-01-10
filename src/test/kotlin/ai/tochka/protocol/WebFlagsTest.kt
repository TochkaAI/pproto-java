package ai.tochka.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class WebFlagsTest : ProtocolTests(useWebFlags = true) {
    data class TestData(
        val foo: String,
        var bar: Int
    )

    @Test
    fun testCommand() {
        registry.registerContentType(MessageType.COMMAND, "test", TestData::class.java)

        val msg1 = Message(
            id = "1",
            type = MessageType.COMMAND,
            command = "test",
            content = TestData(foo = "abc", bar = 123),
            priority = MessagePriority.NORMAL,
            status = MessageStatus.SUCCESS
        )
        val str1 = registry.serialize(msg1)
        assertEquals(
            """{"id":"1","command":"test","flags":2164267081,"content":{"foo":"abc","bar":123},"webFlags":{"type":"command","execStatus":"success","priority":"normal","contentFormat":"json"},"tags":null}""",
            str1
        )
        assertEquals(msg1, registry.deserialize(str1).first)

        val msg2 = Message(
            id = "2",
            type = MessageType.COMMAND,
            command = "test",
            content = TestData(foo = "def", bar = 456),
            priority = MessagePriority.HIGH,
            status = MessageStatus.SUCCESS
        )
        val str2 = registry.serialize(msg2)
        assertEquals(
            """{"id":"2","command":"test","flags":2164267017,"content":{"foo":"def","bar":456},"webFlags":{"type":"command","execStatus":"success","priority":"high","contentFormat":"json"},"tags":null}""",
            str2
        )
        assertEquals(msg2, registry.deserialize(str2).first)
    }

    @Test
    fun testEvent() {
        registry.registerContentType(MessageType.EVENT, "test", TestData::class.java)

        val msg = Message(
            id = "1",
            type = MessageType.EVENT,
            command = "test",
            content = TestData(foo = "abc", bar = 123),
            priority = MessagePriority.LOW,
            status = MessageStatus.SUCCESS
        )
        val str = registry.serialize(msg)
        assertEquals(
            """{"id":"1","command":"test","flags":2164267147,"content":{"foo":"abc","bar":123},"webFlags":{"type":"event","execStatus":"success","priority":"low","contentFormat":"json"},"tags":null}""",
            str
        )
        assertEquals(msg, registry.deserialize(str).first)
    }

    @Test
    fun testAnswer() {
        registry.registerContentType(MessageType.ANSWER, "test", TestData::class.java)

        val msg1 = Message(
            id = "1",
            type = MessageType.ANSWER,
            command = "test",
            content = TestData(foo = "abc", bar = 123),
            priority = MessagePriority.NORMAL,
            status = MessageStatus.SUCCESS
        )
        val str1 = registry.serialize(msg1)
        assertEquals(
            """{"id":"1","command":"test","flags":2164267082,"content":{"foo":"abc","bar":123},"webFlags":{"type":"answer","execStatus":"success","priority":"normal","contentFormat":"json"},"tags":null}""",
            str1
        )
        assertEquals(msg1, registry.deserialize(str1).first)

        val msg2 = Message(
            id = "2",
            type = MessageType.ANSWER,
            command = "test",
            content = MessageError(
                group = 1,
                code = "foo",
                description = "bar"
            ),
            priority = MessagePriority.HIGH,
            status = MessageStatus.FAILED
        )
        val str2 = registry.serialize(msg2)
        assertEquals(
            """{"id":"2","command":"test","flags":2164267026,"content":{"group":1,"code":"foo","description":"bar"},"webFlags":{"type":"answer","execStatus":"failed","priority":"high","contentFormat":"json"},"tags":null}""",
            str2
        )
        assertEquals(
            ProtocolAnswerException(group = 1, code = "foo", message = "bar"),
            registry.deserialize(str2).second
        )
    }
}