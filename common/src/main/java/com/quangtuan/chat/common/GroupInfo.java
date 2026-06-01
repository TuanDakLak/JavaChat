package com.quangtuan.chat.common;

import java.io.Serializable;

public record GroupInfo(int id, String name) implements Serializable {
    @Override
    public String toString() {
        return name;
    }
}
