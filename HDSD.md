# HƯỚNG DẪN SỬ DỤNG (HDSD)
## CÔNG CỤ KIỂM THỬ AMHS/SWIM GATEWAY

---

## 1. GIỚI THIỆU

Công cụ Kiểm thử AMHS/SWIM Gateway là một ứng dụng Java giúp kiểm tra và xác minh khả năng tương tác của hệ thống cửa ngõ (gateway) với các dịch vụ AMHS (Hệ thống xử lý thư hàng không) và SWIM (Mạng lưới thông tin toàn cầu về hệ thống).

### Tính năng chính:
- ✅ Kiểm tra kết nối mạng đến các máy chủ AMHS/SWIM
- ✅ Xác thực cấu hình TLS/SSL
- ✅ Mô phỏng gửi/nhận bản tin AFTN/AMHS
- ✅ Tạo báo cáo kiểm thử chi tiết
- ✅ Hỗ trợ chạy trên Docker (linh hoạt, dễ triển khai)

---

## 2. YÊU CẦU HỆ THỐNG

### Để chạy trực tiếp (không dùng Docker):
- Java JDK 17 trở lên
- Maven 3.8+
- Hệ điều hành: Windows/Linux/macOS

### Để chạy bằng Docker (KHUYẾN NGHỊ):
- Docker Desktop (Windows/macOS) hoặc Docker Engine (Linux)
- Docker Compose (thường đi kèm với Docker Desktop)
- Không cần cài đặt Java hay Maven trên máy chủ

---

## 3. CÀI ĐẶT VÀ CHẠY TRÊN DOCKER

### Bước 1: Chuẩn bị thư mục

Tạo một thư mục làm việc và sao chép các file cần thiết:

```bash
mkdir amhs-test
cd amhs-test

# Sao chép các file từ dự án gốc
cp /path/to/project/Dockerfile .
cp /path/to/project/docker-compose.yml .
cp -r /path/to/project/config .
cp -r /path/to/project/materials .
mkdir output
```

### Bước 2: Cấu hình tham số kiểm thử

Mở file `config/test-config.properties` và chỉnh sửa các thông số:

```properties
# Địa chỉ máy chủ AMHS/SWIM cần kiểm tra
amhs.server.host=192.168.1.100
amhs.server.port=5050
swim.endpoint.url=https://swim.example.com/api

# Thông tin xác thực (nếu có)
username=testuser
password=testpass

# Thời gian chờ (giây)
connection.timeout=30
read.timeout=60
```

### Bước 3: Chạy container

#### Cách 1: Sử dụng Docker Compose (DỄ NHẤT)

**Chế độ tương tác (có giao diện):**
```bash
docker-compose up interactive
```

**Chế độ tự động (không giao diện, phù hợp CI/CD):**
```bash
docker-compose up headless
```

#### Cách 2: Sử dụng lệnh Docker trực tiếp

**Chạy với giao diện:**
```bash
docker build -t amhs-swim-test-tool:latest .

docker run -it --rm \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/materials:/app/materials:ro \
  -v $(pwd)/output:/app/output \
  --network host \
  amhs-swim-test-tool:latest
```

**Chạy tự động (headless):**
```bash
docker run -it --rm \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/materials:/app/materials:ro \
  -v $(pwd)/output:/app/output \
  --network host \
  -Djava.awt.headless=true \
  amhs-swim-test-tool:latest java -jar target/amhs-swim-test-tool-1.0.0.jar --auto
```

> **Lưu ý cho Windows PowerShell:** Thay `$(pwd)` bằng `${PWD}`

---

## 4. SỬ DỤNG CÔNG CỤ

### 4.1. Giao diện chính

Khi khởi động, bạn sẽ thấy màn hình chính với các tab:

| Tab | Chức năng |
|-----|-----------|
| **Kết nối** | Kiểm tra kết nối đến máy chủ AMHS/SWIM |
| **Xác thực** | Kiểm tra chứng chỉ TLS/SSL |
| **Gửi bản tin** | Mô phỏng gửi bản tin AFTN/AMHS |
| **Nhận bản tin** | Mô phỏng nhận và giải mã bản tin |
| **Báo cáo** | Xem và xuất kết quả kiểm thử |

### 4.2. Các bước kiểm thử cơ bản

#### Bước 1: Kiểm tra kết nối
1. Chọn tab **Kết nối**
2. Nhập địa chỉ IP/hostname và cổng của máy chủ
3. Nhấn nút **Kiểm tra kết nối**
4. Xem kết quả: ✅ Thành công hoặc ❌ Thất bại (kèm lý do)

#### Bước 2: Kiểm tra chứng chỉ SSL/TLS
1. Chọn tab **Xác thực**
2. Nhập URL endpoint SWIM
3. Nhấn **Kiểm tra chứng chỉ**
4. Hệ thống hiển thị thông tin chứng chỉ và cảnh báo (nếu có)

#### Bước 3: Gửi bản tin thử nghiệm
1. Chọn tab **Gửi bản tin**
2. Chọn loại bản tin (AFTN, AMHS, JSON, XML)
3. Nhập nội dung bản tin hoặc tải từ file trong thư mục `materials/`
4. Nhấn **Gửi bản tin**
5. Ghi lại kết quả và thời gian phản hồi

#### Bước 4: Xuất báo cáo
1. Chọn tab **Báo cáo**
2. Nhấn **Xuất báo cáo PDF** hoặc **Xuất báo cáo HTML**
3. File báo cáo được lưu vào thư mục `output/`

---

## 5. THAM SỐ DÒNG LỆNH

Khi chạy ở chế độ tự động (headless), bạn có thể sử dụng các tham số sau:

| Tham số | Mô tả | Ví dụ |
|---------|-------|-------|
| `--auto` | Chạy tự động toàn bộ bài kiểm thử | `--auto` |
| `--config` | Đường dẫn file cấu hình | `--config /app/config/test-config.properties` |
| `--report` | Định dạng báo cáo (pdf/html/json) | `--report html` |
| `--verbose` | Hiển thị thông tin chi tiết | `--verbose` |
| `--help` | Hiển thị trợ giúp | `--help` |

**Ví dụ chạy tự động:**
```bash
docker run -it --rm \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/output:/app/output \
  --network host \
  amhs-swim-test-tool:latest \
  --auto --config /app/config/test-config.properties --report html --verbose
```

---

## 6. XỬ LÝ SỰ CỐ

### Lỗi thường gặp và cách khắc phục

| Lỗi | Nguyên nhân | Giải pháp |
|-----|-------------|-----------|
| `Connection refused` | Máy chủ đích không phản hồi | Kiểm tra IP/cổng, firewall, VPN |
| `Certificate expired` | Chứng chỉ SSL hết hạn | Liên hệ quản trị hệ thống để gia hạn |
| `Permission denied` | Thiếu quyền truy cập file | Đảm bảo user trong container có quyền đọc file |
| `Out of memory` | Thiếu bộ nhớ RAM | Tăng RAM cho Docker trong cài đặt |
| `Network unreachable` | Lỗi mạng | Kiểm tra kết nối mạng, cấu hình DNS |

### Xem log chi tiết

Để xem log khi container chạy:
```bash
docker-compose logs -f interactive
```

Hoặc với Docker thuần:
```bash
docker logs <container_id>
```

---

## 7. CẤU TRÚC THƯ MỤC

```
amhs-test/
├── Dockerfile              # File xây dựng image Docker
├── docker-compose.yml      # Cấu hình Docker Compose
├── config/                 # Thư mục cấu hình
│   └── test-config.properties
├── materials/              # Tài liệu, mẫu bản tin
│   ├── sample-aftn.txt
│   └── sample-amhs.xml
├── output/                 # Kết quả kiểm thử (tự động tạo)
│   ├── report-20240115.html
│   └── log-20240115.txt
└── HDSD.md                 # File hướng dẫn này
```

---

## 8. BẢO MẬT

### Khuyến nghị bảo mật khi triển khai:

1. **Không lưu mật khẩu trong file cấu hình** - Sử dụng biến môi trường hoặc vault
2. **Giới hạn quyền container** - Container chạy với user không phải root
3. **Mã hóa kết nối** - Luôn sử dụng TLS/SSL khi kết nối đến máy chủ thật
4. **Cô lập mạng** - Sử dụng Docker network riêng cho môi trường sản xuất

### Ví dụ sử dụng biến môi trường cho mật khẩu:

```bash
docker run -it --rm \
  -e AMHS_PASSWORD="matkhau_bi_mat" \
  -v $(pwd)/config:/app/config:ro \
  --network host \
  amhs-swim-test-tool:latest
```

Sau đó trong code, đọc biến môi trường thay vì từ file properties.

---

## 9. HỖ TRỢ VÀ LIÊN HỆ

Nếu bạn gặp vấn đề hoặc cần hỗ trợ thêm:

- 📧 Email: support@example.com
- 📞 Hotline: 1900-XXXX
- 📚 Tài liệu kỹ thuật: Xem folder `docs/` trong repository

---

## 10. PHỤ LỤC

### A. Mẫu file cấu hình đầy đủ

```properties
# ===========================================
# CẤU HÌNH KIỂM THỬ AMHS/SWIM GATEWAY
# ===========================================

# Thông tin máy chủ AMHS
amhs.server.host=192.168.1.100
amhs.server.port=5050
amhs.protocol=TCP

# Thông tin endpoint SWIM
swim.endpoint.url=https://swim.example.com/api/v1
swim.api.key=your_api_key_here

# Xác thực
auth.type=basic
auth.username=testuser
# auth.password=Đặt qua biến môi trường AMHS_PASSWORD

# Timeout (giây)
connection.timeout=30
read.timeout=60
write.timeout=60

# Retry settings
retry.count=3
retry.delay=5

# Logging
log.level=INFO
log.file=/app/output/test.log
```

### B. Mẫu bản tin AFTN

```
ZCZC ABCD1234
VVNBZZZX
VTBBZZZX
121000
FVNP1234 VVNBZZZX VTBBZZZX 121000
TEST MESSAGE FROM AMHS TEST TOOL
NNNN
```

### C. Lệnh Docker hữu ích

```bash
# Xem danh sách container đang chạy
docker ps

# Xem tất cả container (kể cả đã dừng)
docker ps -a

# Xóa container đã dừng
docker container prune

# Xóa image không được sử dụng
docker image prune

# Xem kích thước image
docker images amhs-swim-test-tool

# Truy cập vào shell bên trong container
docker exec -it <container_id> /bin/sh
```

---

**Phiên bản tài liệu:** 1.0  
**Ngày cập nhật:** 2024  
**Áp dụng cho phiên bản phần mềm:** 1.0.0

---
*Tài liệu này được biên soạn nhằm hỗ trợ người dùng Việt Nam. Mọi góp ý xin gửi về địa chỉ email hỗ trợ.*
