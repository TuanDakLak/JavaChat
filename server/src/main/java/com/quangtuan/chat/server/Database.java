package com.quangtuan.chat.server;

import com.quangtuan.chat.common.ChatMessage;
import com.quangtuan.chat.common.GroupInfo;
import com.quangtuan.chat.common.UserInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private volatile ServerSettings settings;

    public Database(ServerSettings settings) {
        this.settings = settings;
    }

    public void setSettings(ServerSettings settings) {
        this.settings = settings;
    }

    public Connection connect() throws SQLException {
        ServerSettings current = settings;
        return DriverManager.getConnection(current.jdbcUrl(), current.dbUser(), current.dbPassword());
    }

    public void init() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    create table if not exists users (
                        id serial primary key,
                        username varchar(50) not null unique,
                        password_hash varchar(128) not null,
                        salt varchar(64) not null,
                        created_at timestamp not null default now()
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists chat_groups (
                        id serial primary key,
                        name varchar(80) not null,
                        owner_id int not null references users(id),
                        created_at timestamp not null default now()
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists group_members (
                        group_id int not null references chat_groups(id) on delete cascade,
                        user_id int not null references users(id) on delete cascade,
                        primary key (group_id, user_id)
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists chat_messages (
                        id serial primary key,
                        sender_id int not null references users(id),
                        receiver_user_id int references users(id),
                        group_id int references chat_groups(id),
                        content text,
                        file_name varchar(255),
                        file_data bytea,
                        created_at timestamp not null default now(),
                        check (
                            (receiver_user_id is not null and group_id is null)
                            or (receiver_user_id is null and group_id is not null)
                        )
                    )
                    """);
            st.executeUpdate("""
                    create table if not exists message_deletions (
                        message_id int not null references chat_messages(id) on delete cascade,
                        user_id int not null references users(id) on delete cascade,
                        primary key (message_id, user_id)
                    )
                    """);
        }
    }

    public UserInfo register(String username, String password) throws SQLException {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "insert into users(username, password_hash, salt) values (?, ?, ?) returning id")) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new UserInfo(rs.getInt(1), username);
            }
        }
    }

    public UserInfo login(String username, String password) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("select id, password_hash, salt from users where username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String expected = rs.getString("password_hash");
                String actual = PasswordUtil.hash(password, rs.getString("salt"));
                return expected.equals(actual) ? new UserInfo(rs.getInt("id"), username) : null;
            }
        }
    }

    public UserInfo findUser(String username) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("select id, username from users where username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new UserInfo(rs.getInt("id"), rs.getString("username")) : null;
            }
        }
    }

    public GroupInfo createGroup(String name, int ownerId) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement group = c.prepareStatement(
                    "insert into chat_groups(name, owner_id) values (?, ?) returning id")) {
                group.setString(1, name);
                group.setInt(2, ownerId);
                try (ResultSet rs = group.executeQuery()) {
                    rs.next();
                    int groupId = rs.getInt(1);
                    try (PreparedStatement member = c.prepareStatement(
                            "insert into group_members(group_id, user_id) values (?, ?) on conflict do nothing")) {
                        member.setInt(1, groupId);
                        member.setInt(2, ownerId);
                        member.addBatch();
                        member.executeBatch();
                    }
                    c.commit();
                    return new GroupInfo(groupId, name);
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<GroupInfo> allGroups() throws SQLException {
        List<GroupInfo> groups = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     select id, name
                     from chat_groups
                     order by name
                     """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                groups.add(new GroupInfo(rs.getInt("id"), rs.getString("name")));
            }
        }
        return groups;
    }

    public List<GroupInfo> groupsForUser(int userId) throws SQLException {
        List<GroupInfo> groups = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     select g.id, g.name
                     from chat_groups g
                     join group_members gm on gm.group_id = g.id
                     where gm.user_id = ?
                     order by g.name
                     """)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groups.add(new GroupInfo(rs.getInt("id"), rs.getString("name")));
                }
            }
        }
        return groups;
    }

    public List<Integer> groupMemberIds(int groupId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("select user_id from group_members where group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    public boolean isGroupMember(int groupId, int userId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "select 1 from group_members where group_id = ? and user_id = ?")) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void joinGroup(int groupId, int userId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "insert into group_members(group_id, user_id) values (?, ?) on conflict do nothing")) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void leaveGroup(int groupId, int userId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "delete from group_members where group_id = ? and user_id = ?")) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public ChatMessage saveDirect(int senderId, int receiverId, String content, String fileName, byte[] fileData) throws SQLException {
        return saveMessage(senderId, receiverId, null, content, fileName, fileData);
    }

    public ChatMessage saveGroup(int senderId, int groupId, String content, String fileName, byte[] fileData) throws SQLException {
        return saveMessage(senderId, null, groupId, content, fileName, fileData);
    }

    private ChatMessage saveMessage(int senderId, Integer receiverId, Integer groupId, String content, String fileName, byte[] fileData) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     insert into chat_messages(sender_id, receiver_user_id, group_id, content, file_name, file_data)
                     values (?, ?, ?, ?, ?, ?)
                     returning id, created_at
                     """)) {
            ps.setInt(1, senderId);
            setInteger(ps, 2, receiverId);
            setInteger(ps, 3, groupId);
            ps.setString(4, content);
            ps.setString(5, fileName);
            ps.setBytes(6, fileData);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return messageById(c, rs.getInt("id"));
            }
        }
    }

    public List<ChatMessage> directHistory(int userId, int otherUserId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     select m.*, u.username sender_name, g.name group_name
                     from chat_messages m
                     join users u on u.id = m.sender_id
                     left join chat_groups g on g.id = m.group_id
                     left join message_deletions d on d.message_id = m.id and d.user_id = ?
                     where d.message_id is null
                       and m.group_id is null
                       and ((m.sender_id = ? and m.receiver_user_id = ?) or (m.sender_id = ? and m.receiver_user_id = ?))
                     order by m.created_at
                     """)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, otherUserId);
            ps.setInt(4, otherUserId);
            ps.setInt(5, userId);
            return messages(ps);
        }
    }

    public List<ChatMessage> groupHistory(int userId, int groupId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("""
                     select m.*, u.username sender_name, g.name group_name
                     from chat_messages m
                     join users u on u.id = m.sender_id
                     left join chat_groups g on g.id = m.group_id
                     left join message_deletions d on d.message_id = m.id and d.user_id = ?
                     where d.message_id is null and m.group_id = ?
                     order by m.created_at
                     """)) {
            ps.setInt(1, userId);
            ps.setInt(2, groupId);
            return messages(ps);
        }
    }

    public void deleteForUser(int messageId, int userId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "insert into message_deletions(message_id, user_id) values (?, ?) on conflict do nothing")) {
            ps.setInt(1, messageId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private ChatMessage messageById(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                select m.*, u.username sender_name, g.name group_name
                from chat_messages m
                join users u on u.id = m.sender_id
                left join chat_groups g on g.id = m.group_id
                where m.id = ?
                """)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return mapMessage(rs);
            }
        }
    }

    private List<ChatMessage> messages(PreparedStatement ps) throws SQLException {
        List<ChatMessage> rows = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapMessage(rs));
            }
        }
        return rows;
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        Integer receiverId = (Integer) rs.getObject("receiver_user_id");
        Integer groupId = (Integer) rs.getObject("group_id");
        Timestamp timestamp = rs.getTimestamp("created_at");
        return new ChatMessage(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                rs.getString("sender_name"),
                receiverId,
                groupId,
                rs.getString("group_name"),
                rs.getString("content"),
                rs.getString("file_name"),
                rs.getBytes("file_data"),
                timestamp == null ? null : timestamp.toLocalDateTime()
        );
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, value);
        }
    }
}
