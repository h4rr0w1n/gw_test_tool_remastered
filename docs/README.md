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

## 4. Hướng dẫn sử dụng
1. Cấu hình file `config/test.properties` với thông tin kết nối.
2. Chạy `mvn clean package`.
3. Thực thi `java -jar target/amhs-swim-test-tool.jar`.
4. Chọn test case từ danh sách và nhấn "Execute".
5. Xem kết quả trong khung Log.

## 5. Ánh xạ API
Xem chi tiết trong file `API_MAPPING.md`.