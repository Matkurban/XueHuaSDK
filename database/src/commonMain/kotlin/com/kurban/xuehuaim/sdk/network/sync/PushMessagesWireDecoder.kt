package com.kurban.xuehuaim.sdk.network.sync

/**
 * Minimal protobuf decoder for push payloads used on platforms without protokt runtime (iOS, Wasm).
 */
internal object PushMessagesWireDecoder {
    fun decode(data: ByteArray): PullMsgResp {
        val reader = ProtoReader(data)
        val msgs = mutableMapOf<String, MsgList>()
        val notificationMsgs = mutableMapOf<String, MsgList>()
        while (true) {
            val field = reader.readFieldNumber() ?: break
            when (field) {
                1 -> msgs.putAll(readStringPullMsgsMap(reader.readLengthDelimited()))
                2 -> notificationMsgs.putAll(readStringPullMsgsMap(reader.readLengthDelimited()))
                else -> reader.skipField()
            }
        }
        return PullMsgResp(msgs = msgs, notificationMsgs = notificationMsgs)
    }

    private fun readStringPullMsgsMap(entryBytes: ByteArray): Map<String, MsgList> {
        val entryReader = ProtoReader(entryBytes)
        var key = ""
        var pullMsgs = MsgList()
        while (true) {
            when (val field = entryReader.readFieldNumber() ?: break) {
                1 -> key = entryReader.readString()
                2 -> pullMsgs = readPullMsgs(entryReader.readLengthDelimited())
                else -> entryReader.skipField()
            }
        }
        return if (key.isEmpty()) emptyMap() else mapOf(key to pullMsgs)
    }

    private fun readPullMsgs(bytes: ByteArray): MsgList {
        val reader = ProtoReader(bytes)
        val messages = mutableListOf<WsMsgData>()
        while (true) {
            when (val field = reader.readFieldNumber() ?: break) {
                1 -> messages += readMsgData(reader.readLengthDelimited())
                else -> reader.skipField()
            }
        }
        return MsgList(msgs = messages)
    }

    private fun readMsgData(bytes: ByteArray): WsMsgData {
        val reader = ProtoReader(bytes)
        var sendId: String? = null
        var recvId: String? = null
        var groupId: String? = null
        var clientMsgId = ""
        var serverMsgId: String? = null
        var senderNickname: String? = null
        var senderFaceUrl: String? = null
        var sessionType: Int? = null
        var contentType: Int? = null
        var content: String? = null
        var seq = 0L
        var sendTime: Long? = null
        var createTime: Long? = null
        while (true) {
            when (val field = reader.readFieldNumber() ?: break) {
                1 -> sendId = reader.readString()
                2 -> recvId = reader.readString()
                3 -> groupId = reader.readString()
                4 -> clientMsgId = reader.readString()
                5 -> serverMsgId = reader.readString()
                7 -> senderNickname = reader.readString()
                8 -> senderFaceUrl = reader.readString()
                9 -> sessionType = reader.readInt32()
                11 -> contentType = reader.readInt32()
                12 -> content = reader.readBytes().decodeToString()
                14 -> seq = reader.readInt64()
                15 -> sendTime = reader.readInt64()
                16 -> createTime = reader.readInt64()
                else -> reader.skipField()
            }
        }
        return WsMsgData(
            clientMsgID = clientMsgId,
            serverMsgID = serverMsgId,
            sendID = sendId,
            recvID = recvId,
            groupID = groupId,
            contentType = contentType,
            content = content,
            seq = seq,
            sendTime = sendTime,
            createTime = createTime,
            sessionType = sessionType,
            senderNickname = senderNickname,
            senderFaceURL = senderFaceUrl,
        )
    }

    private class ProtoReader(private val data: ByteArray) {
        private var offset = 0
        private var lastWireType = 0

        fun readFieldNumber(): Int? {
            if (offset >= data.size) return null
            val tag = readVarintInt()
            lastWireType = tag and 0x7
            return tag ushr 3
        }

        fun skipField() {
            when (lastWireType) {
                0 -> readVarintLong()
                1 -> offset += 8
                2 -> offset += readVarintInt()
                5 -> offset += 4
                else -> offset = data.size
            }
        }

        fun readInt32(): Int = readVarintInt()

        fun readInt64(): Long = readVarintLong()

        fun readLengthDelimited(): ByteArray {
            val length = readVarintInt()
            val start = offset
            offset += length
            return data.copyOfRange(start, offset)
        }

        fun readString(): String = readLengthDelimited().decodeToString()

        fun readBytes(): ByteArray = readLengthDelimited()

        private fun readVarintInt(): Int = readVarintLong().toInt()

        private fun readVarintLong(): Long {
            var result = 0L
            var shift = 0
            while (offset < data.size) {
                val b = data[offset++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }
    }
}
