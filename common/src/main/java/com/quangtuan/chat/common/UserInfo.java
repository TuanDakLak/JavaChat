package com.quangtuan.chat.common;

import java.io.Serializable;

public record UserInfo(int id, String username) implements Serializable {
    @Override
    public String toString() {
        return username;
    }
}
