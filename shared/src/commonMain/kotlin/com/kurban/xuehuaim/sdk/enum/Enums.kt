package com.kurban.xuehuaim.sdk.enum

import com.kurban.xuehuaim.sdk.serialization.intEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LoginStatus(val value: Int) {
    @SerialName("1")
    LOGOUT(1),

    @SerialName("2")
    LOGGING(2),

    @SerialName("3")
    LOGGED(3),
}

enum class TokenCheckResult {
    Valid,
    Invalid,
    NetworkError,
}

enum class FavoriteType(val value: String) {
    MESSAGE("message"),
    MOMENT_CONTENT("moment_content"),
    MOMENT_COMMENT("moment_comment"),
    LINK("link"),
    NOTE("note"),
    ;

    companion object {
        fun fromValue(value: String?): FavoriteType =
            entries.find { it.value == value } ?: MESSAGE
    }
}

@Serializable
enum class IMPlatform(val value: Int) {
    IOS(1),
    ANDROID(2),
    WINDOWS(3),
    XOS(4),
    WEB(5),
    MINI_WEB(6),
    LINUX(7),
    ANDROID_PAD(8),
    IPAD(9);

    companion object {
        fun fromValue(value: Int): IMPlatform = entries.find { it.value == value } ?: WEB
    }
}

@Serializable
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

@Serializable(with = ConversationTypeSerializer::class)
enum class ConversationType(val value: Int) {
    SINGLE(1),
    SUPER_GROUP(3),
    NOTIFICATION(4),
    ;

    companion object {
        fun fromValue(value: Int): ConversationType? = entries.find { it.value == value }
    }
}

internal object ConversationTypeSerializer :
    kotlinx.serialization.KSerializer<ConversationType> by intEnumSerializer(
        serialName = "ConversationType",
        values = ConversationType.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = { ConversationType.fromValue(it) ?: ConversationType.SINGLE },
    )

@Serializable(with = MessageStatusSerializer::class)
enum class MessageStatus(val value: Int) {
    SENDING(1),
    SEND_SUCCESS(2),
    SEND_FAILED(3),
    DELETED(4),
    ;

    companion object {
        fun fromValue(value: Int): MessageStatus? = entries.find { it.value == value }
    }
}

internal object MessageStatusSerializer :
    kotlinx.serialization.KSerializer<MessageStatus> by intEnumSerializer(
        serialName = "MessageStatus",
        values = MessageStatus.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = { MessageStatus.fromValue(it) ?: MessageStatus.SENDING },
    )

@Serializable(with = ReceiveMessageOptSerializer::class)
enum class ReceiveMessageOpt(val value: Int) {
    NORMAL(0),
    NOT_RECEIVE(1),
    NOT_NOTIFY(2),
    ;

    companion object {
        fun fromValue(value: Int): ReceiveMessageOpt =
            entries.find { it.value == value } ?: NORMAL
    }
}

internal object ReceiveMessageOptSerializer :
    kotlinx.serialization.KSerializer<ReceiveMessageOpt> by intEnumSerializer(
        serialName = "ReceiveMessageOpt",
        values = ReceiveMessageOpt.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = ReceiveMessageOpt::fromValue,
    )

@Serializable(with = GroupAtTypeSerializer::class)
enum class GroupAtType(val value: Int) {
    NORMAL(0),
    AT_ME(1),
    AT_ALL(2),
    AT_ALL_AT_ME(3),
    ;

    companion object {
        fun fromValue(value: Int): GroupAtType? = entries.find { it.value == value }
    }
}

internal object GroupAtTypeSerializer :
    kotlinx.serialization.KSerializer<GroupAtType> by intEnumSerializer(
        serialName = "GroupAtType",
        values = GroupAtType.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = { GroupAtType.fromValue(it) ?: GroupAtType.NORMAL },
    )

@Serializable(with = MessageTypeSerializer::class)
enum class MessageType(val value: Int) {
    TEXT(101),
    PICTURE(102),
    VOICE(103),
    VIDEO(104),
    FILE(105),
    AT_TEXT(106),
    MERGER(107),
    CARD(108),
    LOCATION(109),
    CUSTOM(110),
    TYPING(113),
    QUOTE(114),
    CUSTOM_FACE(115),
    ADVANCED_TEXT(117),
    MARKDOWN_TEXT(118),
    CUSTOM_MSG_ONLINE_ONLY(120),
    CALL_SIGNAL(124),
    RED_PACKET(125),
    RED_PACKET_GRAB_NOTIFY(126),
    NOTIFICATION_BEGIN(1000),
    FRIEND_NOTIFICATION_BEGIN(1200),
    FRIEND_APPLICATION_APPROVED(1201),
    FRIEND_APPLICATION_REJECTED(1202),
    FRIEND_APPLICATION(1203),
    FRIEND_ADDED(1204),
    FRIEND_DELETED(1205),
    FRIEND_REMARK_SET(1206),
    BLACK_ADDED(1207),
    BLACK_DELETED(1208),
    FRIEND_INFO_UPDATED(1209),
    FRIENDS_INFO_UPDATE(1210),
    CONVERSATION_CHANGE(1300),
    USER_INFO_UPDATED(1303),
    GROUP_NOTIFICATION_BEGIN(1500),
    GROUP_CREATED(1501),
    GROUP_INFO_SET(1502),
    JOIN_GROUP_APPLICATION(1503),
    MEMBER_QUIT(1504),
    GROUP_APPLICATION_ACCEPTED(1505),
    GROUP_APPLICATION_REJECTED(1506),
    GROUP_OWNER_TRANSFERRED(1507),
    MEMBER_KICKED(1508),
    MEMBER_INVITED(1509),
    MEMBER_ENTER(1510),
    GROUP_DISMISSED(1511),
    GROUP_MEMBER_MUTED(1512),
    GROUP_MEMBER_CANCEL_MUTED(1513),
    GROUP_MUTED(1514),
    GROUP_CANCEL_MUTED(1515),
    GROUP_MEMBER_INFO_SET(1516),
    GROUP_MEMBER_SET_TO_ADMIN(1517),
    GROUP_MEMBER_SET_TO_ORDINARY(1518),
    GROUP_INFO_SET_ANNOUNCEMENT(1519),
    GROUP_INFO_SET_NAME(1520),
    BURN_AFTER_READING(1701),
    BUSINESS_NOTIFICATION(2001),
    MSG_REVOKE(2101),
    MSG_DELETE(2102),
    MSG_HAS_READ_RECEIPT(2200);


    companion object {
        fun fromValue(value: Int): MessageType? = entries.find { it.value == value }
    }
}

internal object MessageTypeSerializer :
    kotlinx.serialization.KSerializer<MessageType> by intEnumSerializer(
        serialName = "MessageType",
        values = MessageType.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = { MessageType.fromValue(it) ?: MessageType.TEXT },
    )

@Serializable(with = GroupRoleLevelSerializer::class)
enum class GroupRoleLevel(val value: Int) {
    NORMAL(1),
    ADMIN(2),
    OWNER(3),
    ;

    companion object {
        fun fromValue(value: Int): GroupRoleLevel? = entries.find { it.value == value }
    }
}

internal object GroupRoleLevelSerializer :
    kotlinx.serialization.KSerializer<GroupRoleLevel> by intEnumSerializer(
        serialName = "GroupRoleLevel",
        values = GroupRoleLevel.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = { GroupRoleLevel.fromValue(it) ?: GroupRoleLevel.NORMAL },
    )

@Serializable(with = GroupTypeSerializer::class)
enum class GroupType(val value: Int) {
    NORMAL(0),
    SUPER(1),
    WORKING(2),
    ;

    companion object {
        fun fromValue(value: Int): GroupType? = entries.find { it.value == value }
    }
}

internal object GroupTypeSerializer :
    kotlinx.serialization.KSerializer<GroupType> by intEnumSerializer(
        serialName = "GroupType",
        values = GroupType.entries.toTypedArray(),
        valueOf = { it.value },
        unknownFallback = { GroupType.fromValue(it) ?: GroupType.NORMAL },
    )

@Serializable
enum class CallType(val value: Int) {
    AUDIO(1),
    VIDEO(2),
    ;

    fun apiValue(): String = when (this) {
        VIDEO -> "video"
        AUDIO -> "audio"
    }

    companion object {
        fun fromApiValue(value: String): CallType =
            if (value.equals("video", ignoreCase = true)) VIDEO else AUDIO
    }
}

@Serializable
enum class CallState(val value: Int) {
    IDLE(0),
    CALLING(1),
    RINGING(2),
    CONNECTED(3),
    ENDED(4),
}
