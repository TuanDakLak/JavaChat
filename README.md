# JavaChat

Ứng dụng chat realtime bằng Java Swing, Java Socket và PostgreSQL. Project dùng Maven multi-module:

- `common`: protocol, DTO dùng chung giữa server và client.
- `server`: socket server, JDBC PostgreSQL, lưu user/group/message/file/history.
- `client`: giao diện Swing dạng tab dọc bên trái.

## Cấu hình database

Tạo database PostgreSQL trước khi chạy server:

```sql
create database "javaChat";
```

Thông tin mặc định đã khớp đề bài:

```text
DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=123
DB_NAME=javaChat
CHAT_PORT=5555
```

Server tự tạo bảng khi khởi động. Có thể đổi cấu hình bằng environment variable cùng tên.

## Build

Cần JDK 17+ và Maven.

```bash
mvn clean package
```

Sau khi build, jar thực thi nằm tại:

```text
server/target/javachat-server-1.0-SNAPSHOT.jar
client/target/javachat-client-1.0-SNAPSHOT.jar
```

## Chạy

Chạy server trước:

```bash
java -jar server/target/javachat-server-1.0-SNAPSHOT.jar
```

Mở mỗi client trong một terminal khác:

```bash
java -jar client/target/javachat-client-1.0-SNAPSHOT.jar
```

## Sử dụng

1. Tab `Đăng nhập`: nhập server, port, username, password. Bấm `Đăng ký`, sau đó bấm `Đăng nhập`.
2. Tab `Chat`: chọn một user online rồi bấm `Mở chat riêng`. Có thể mở nhiều hội thoại cùng lúc, mỗi hội thoại là một tab.
3. Trong hội thoại: nhập tin nhắn, chọn file nếu cần, bấm `Gửi`.
4. Tab `Nhóm`: chọn nhiều user online, bấm `Tạo group từ user online đã chọn`, sau đó mở chat group.
5. Tab `Lịch sử`: tải lịch sử chat riêng hoặc group, chọn dòng để xoá khỏi lịch sử của tài khoản hiện tại. Nếu message có file, có thể bấm `Lưu file đã chọn`.

## Ghi chú nộp bài

Khi nộp `MSSV.zip`, nên gồm:

- Source code project này.
- Hai file jar trong `server/target` và `client/target`.
- File hướng dẫn sử dụng này, hoặc chuyển nội dung sang `.doc`.
- Clip demo: chạy server, mở 2 client, đăng ký/đăng nhập, chat riêng, chat group, gửi file, xem và xoá lịch sử.
