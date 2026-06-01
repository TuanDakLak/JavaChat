package com.quangtuan.chat.common;

public enum PacketType {
    REGISTER,
    LOGIN,
    LOGOUT,
    ONLINE_USERS,
    CREATE_GROUP,
    JOIN_GROUP,
    LEAVE_GROUP,
    GROUPS,
    SEND_DIRECT,
    SEND_GROUP,
    VOICE_DIRECT,
    VOICE_GROUP,
    VOICE_FRAME,
    MESSAGE,
    HISTORY,
    DELETE_HISTORY,
    OK,
    ERROR
}
