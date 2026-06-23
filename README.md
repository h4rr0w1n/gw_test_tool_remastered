# Công Cụ Kiểm Thử AMHS/SWIM Gateway

## 1. Giới thiệu
Công cụ này được thiết kế để tự động hóa các kịch bản kiểm thử định nghĩa trong tài liệu "Appendix A - AMHS_SWIM Gateway Testing Plan v3.0". Nó cho phép người kiểm thử chọn và chạy từng trường hợp kiểm thử (Test Case) thông qua giao diện đồ họa.

## 2. Kiến trúc
Công cụ sử dụng kiến trúc hướng đối tượng với các module chính:
- **GUI**: Giao diện người dùng (Java Swing).
- **Driver**: Các lớp trừu tượng hóa API bên thứ 3 (X.400, AMQP, Directory).
- **TestCase**: Implement logic cho từng mã test (CTSW001 - CTSW116).
- **Config**: Quản lý cấu hình kết nối.
- **Util**: Tiện ích logging và validation.

## 3. Yêu cầu hệ thống (End User)
- **Java JRE/JDK 8 trở lên** — tải tại https://adoptium.net/ (miễn phí, khuyến nghị)
- **Không cần Maven, Python, hay cấu hình PATH** — chỉ cần Java là chạy được.

## 4. Hướng dẫn sử dụng

### 4.1. Chạy Main Tool
Double-click (hoặc chạy trong terminal):
```bat
run-tool.bat
```
Script sẽ tự động tìm Java trên máy (kể cả khi Java không có trong PATH) và khởi chạy công cụ từ file JAR đã được build sẵn.

### 4.2. Chạy Verifier (nhận và kiểm tra message từ broker)
Double-click (hoặc chạy trong terminal):
```bat
run-verifier.bat [queue-address] [amqp-url] [vpn-name]
```

Nếu không truyền tham số, script sẽ dùng giá trị mặc định từ `config\test.properties`.

**Ví dụ:**
```bat
:: Dùng cấu hình mặc định
run-verifier.bat

:: Chỉ định queue và kết nối
run-verifier.bat TEST.QUEUE amqp://user:pass@192.168.1.10:5672 MY_VPN
```

---

## 5. Dành cho Developer

### 5.1. Build JAR từ source (cần Maven + JDK)
```bat
build.bat
```
Output: `amhs-swim-tool.jar` (fat JAR, tất cả dependencies được bundle).

### 5.2. Tạo release package
```bat
package-release.bat
```
Output: `amhs-swim-gateway-test-tool.zip` (chứa JAR + 2 scripts + config).

### 5.3. Build thủ công
```bat
mvn clean package
java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
```

## 6. Ánh xạ API
Xem chi tiết trong file `API_MAPPING.md`.