package com.quangtuan.chat.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ChatMessage(
        int id,
        int senderId,
        String senderName,
        Integer receiverUserId,
        Integer groupId,
        String groupName,
        String content,
        String fileName,
        byte[] fileData,
        LocalDateTime createdAt
) implements Serializable {
    public boolean hasFile() {
        return fileName != null && !fileName.isBlank() && fileData != null;
    }
}
