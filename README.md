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

## 3. Yêu cầu hệ thống
- Java JDK 11 trở lên.
- Maven 3.6+.
- Truy cập mạng tới AMHS MTA, SWIM Broker (Solace), và Directory Server.
- Thư viện JNI cho Isode API (X400, DSAPI, ATNDS) phải được biên dịch và đưa vào `lib/`.
- Thư viện Solace JCSMP cho kết nối AMQP.

## 4. Hướng dẫn sử dụng / Usage Guide

### 4.1. Chạy bằng Single Run Script (Khuyến nghị)
Công cụ đã được đơn giản hóa với **một script duy nhất** (`run.bat`) có thể chạy trên cả Windows và Linux, bao gồm cả chức năng verifier.

#### Windows:
```bat
:: Run the main test tool
run.bat

:: Run the verifier for CTSW116 validation (direct script)
verifier.bat [queue-or-topic-address] [amqp-url] [vpn-name]

:: Or via run.bat
run.bat --verifier [queue-or-topic-address] [amqp-url] [vpn-name]

:: Examples:
verifier.bat TEST.QUEUE
verifier.bat "my/test/topic" "amqp://user:pass@host:5672" "MY_VPN"
```

Script sẽ tự động:
1. Kiểm tra Java, Maven và Python (cho verifier mode)
2. Tải xuống dependencies (Solace JCSMP) nếu thiếu
3. Cài đặt python-qpid-proton nếu thiếu (cho verifier mode)
4. Biên dịch project nếu cần
5. Chạy công cụ với classpath chính xác

#### Linux/macOS:
```bash
chmod +x run.bat
./run.bat

# Run verifier
./run.bat --verifier [queue-or-topic-address] [amqp-url] [vpn-name]
```

### 4.2. Chạy trực tiếp bằng Java
```bash
mvn clean package
java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
```

### 4.3. Chạy bằng Docker
Xem chi tiết cách chạy bằng Docker trong tài liệu:
- English: [DOCKER_README.md](../DOCKER_README.md)
- Tiếng Việt: [huong-dan-su-dung.md](huong-dan-su-dung.md)

## 5. Ánh xạ API
Xem chi tiết trong file `API_MAPPING.md`.