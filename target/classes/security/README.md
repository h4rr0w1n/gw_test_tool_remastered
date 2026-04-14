<!-- File: src/main/resources/security/README.md -->
# Hướng dẫn Bảo mật và Kho khóa (Security & Keystore)

## Giới thiệu
Thư mục này chứa các tài nguyên bảo mật cần thiết cho AMHS/SWIM Gateway Test Tool, bao gồm kho khóa (keystore) để ký và xác thực message theo chuẩn AMHS Security (EUR Doc 047).

## Yêu cầu tệp tin
Tệp tin `keystore.p12` **không được cung cấp sẵn** trong repository này vì lý do bảo mật. Người dùng cần tự tạo hoặc cung cấp tệp tin này từ hệ thống nội bộ.

## Cách tạo Keystore (PKCS#12)
Sử dụng công cụ `keytool` đi kèm với Java JDK để tạo keystore mới:

```bash
keytool -genkeypair -alias gateway_identity -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 365