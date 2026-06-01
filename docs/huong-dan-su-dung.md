# Hướng dẫn sử dụng — AMHS/SWIM Gateway Test Tool

## 1) Mục đích và phạm vi
AMHS/SWIM Gateway Test Tool là công cụ kiểm thử theo “ICAO EUR Doc 047 – Appendix A (AMHS-SWIM Gateway Testing Plan)”, cho phép:
- Chọn và thực thi các test case CTSW101–CTSW116.
- Phun (publish) bản tin AMQP lên SWIM broker và (tùy kịch bản) nhận/kiểm tra bản tin.
- Ghi nhận kết quả theo phiên làm việc và xuất báo cáo Excel (.xlsx).

Điểm vào (entrypoint) của ứng dụng là `com.amhs.swim.test.Main` trong [Main.java](file:///workspace/src/main/java/com/amhs/swim/test/Main.java).

## 2) Cài đặt
### 2.1 Yêu cầu tiên quyết
- Java: JDK 11+
- Maven: 3.6+
- Kết nối mạng tới SWIM Broker (Solace hoặc AMQP 1.0 broker tương thích)
- Nếu sử dụng phần “verify CTSW116” bằng Python: Python 3 và thư viện `python-proton` (script sẽ kiểm tra)

### 2.2 Biên dịch
Trong thư mục gốc dự án:

```bash
mvn clean package
```

Sau khi build thành công, file jar nên dùng là:
- `target/test-tool-1.0.0-jar-with-dependencies.jar` (khuyến nghị)

Tham chiếu cấu hình build: [pom.xml](file:///workspace/pom.xml).

### 2.3 Chạy công cụ
Linux/macOS:

```bash
chmod +x run_tool.sh
./run_tool.sh
```

Windows:

```bat
run_tool.bat
```

Hoặc chạy trực tiếp:

```bash
java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
```

### 2.4 Chạy bằng Docker (Khuyến nghị)
Bạn có thể dùng Docker để chạy công cụ mà không cần cài đặt Java hay Maven.
1. **Dùng Docker Compose:**
   ```bash
   docker-compose up --build
   ```
2. **Dùng Docker trực tiếp:**
   Xây dựng image:
   ```bash
   docker build -t amhs-swim-test-tool:latest .
   ```
   Chạy container (sử dụng network host để kết nối trực tiếp đến broker):
   ```bash
   docker run -it --rm \
     -v $(pwd)/config:/app/config:ro \
     -v $(pwd)/materials:/app/materials:ro \
     -v $(pwd)/output:/app/output \
     --network host \
     amhs-swim-test-tool:latest
   ```

## 3) Cấu hình (Settings và file)
Công cụ tải cấu hình theo cơ chế:
1. Mặc định: file cấu hình “embedded” trong jar: [src/main/resources/config/test.properties](file:///workspace/src/main/resources/config/test.properties)
2. Ghi đè (nếu tồn tại): file ngoài: [config/test.properties](file:///workspace/config/test.properties)
3. Thay đổi trong GUI có thể được lưu ra `config/test.properties` bằng nút “Save & Apply” (xem [TestFrame.java](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L459-L477))

### 3.1 Các tham số quan trọng
Mở `config/test.properties` để chỉnh thủ công (hoặc cấu hình trong Settings):
- `swim.broker.host`: host/IP broker
- `swim.broker.port`: port broker
- `swim.broker.user`: username
- `swim.broker.password`: password
- `swim.broker.vpn`: VPN (Solace) hoặc vhost (tùy broker)
- `gateway.default_topic`: topic đích mặc định
- `gateway.default_queue`: queue đích mặc định
- `gateway.default_originator`: originator mặc định

Lưu ý thực tế trong repo:
- File config hiện có thể chứa `amqp_broker_profile=...` (underscore) như trong [config/test.properties](file:///workspace/config/test.properties#L4-L9). Đồng thời một số phần logic runtime lại đọc `amqp.broker.profile` (dot). Nếu bạn dựa vào “Broker Profile” để thay đổi hành vi adapter, hãy kiểm tra kỹ (xem [QpidSwimAdapter.isSolaceProfile](file:///workspace/src/main/java/com/amhs/swim/test/driver/QpidSwimAdapter.java#L824-L827)).

### 3.2 Kiểm tra kết nối
Trong Settings, dùng nút “Check Connection” để test kết nối broker (xem [TestFrame.java](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L450-L456)).

Nếu test thất bại:
- Kiểm tra host/port/user/password/vpn
- Kiểm tra firewall/route tới broker
- Xác nhận broker đang chạy và cho phép kết nối

## 4) Quy trình làm việc (process) đề xuất
Một quy trình vận hành kiểm thử điển hình:
1. Chuẩn bị môi trường: broker SWIM, gateway AMHS/SWIM, các queue/topic cần thiết, quyền publish/consume.
2. Cấu hình công cụ:
   - Thiết lập kết nối broker và các giá trị mặc định (topic/queue/originator).
   - Bấm “Check Connection”.
   - “Save & Apply” để lưu cấu hình.
3. Thực thi test case:
   - Chọn CTSWxxx trong cây danh sách test case.
   - Kiểm tra/điều chỉnh payload và application properties theo yêu cầu kịch bản.
   - Nhấn “Execute” (hoặc chức năng chạy theo lô nếu UI hỗ trợ).
4. Đối soát kết quả:
   - Xem log trong UI.
   - Đối soát với hệ thống gateway/AMHS console theo tiêu chí của Doc 047.
5. Xuất báo cáo:
   - Xuất file `.xlsx` theo phiên để lưu hồ sơ kiểm thử.

## 5) Thực hiện các bài test và tùy biến payload
### 5.1 Chọn test case
Tool nạp danh sách case từ [cases.json](file:///workspace/cases.json) qua [TestbookLoader](file:///workspace/src/main/java/com/amhs/swim/test/util/TestbookLoader.java).

### 5.2 Payload mặc định và payload tùy chỉnh
Công cụ hỗ trợ payload mặc định và khả năng tùy biến theo từng CTSW và theo từng message trong case:
- Payload mặc định theo chuẩn nằm trong: [config/default_case_payloads.xml](file:///workspace/config/default_case_payloads.xml)
- Payload tùy chỉnh người dùng được lưu tại: `config/case_payloads.json`
- Cơ chế đọc/ghi do [CaseConfigManager](file:///workspace/src/main/java/com/amhs/swim/test/config/CaseConfigManager.java#L27-L175) quản lý

Khi bạn thay đổi nội dung payload (qua UI nếu có chức năng chỉnh payload) và lưu, file `config/case_payloads.json` sẽ được tạo/cập nhật để ghi đè payload mặc định.

## 6) Tạo báo cáo (Excel)
Trong Settings → “Session Reporting”:
- “Export Report (.xlsx)” sẽ tạo file Excel cho phiên test (xem [TestFrame.java](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L488-L505)).
- “Clear Results” xóa dữ liệu kết quả của phiên hiện tại (xem [TestFrame.java](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L509-L515)).

Gợi ý vận hành:
- Tạo 1 báo cáo/1 phiên kiểm thử (ví dụ theo ngày hoặc theo vòng nghiệm thu) để dễ truy vết.
- Đặt tên file báo cáo theo chuẩn nội bộ (ví dụ: `AMHS_SWIM_Test_Report_YYYYMMDD.xlsx`).

## 7) CTSW116 — xác minh payload theo kịch bản riêng
Repo có công cụ hỗ trợ verify CTSW116 qua Python consumer:
- Script chạy: [verify_payload.sh](file:///workspace/verify_payload.sh)
- Consumer: [verify_ctsw116_consumer.py](file:///workspace/verify_ctsw116_consumer.py)

Cách chạy (Linux/macOS):

```bash
chmod +x verify_payload.sh
./verify_payload.sh
```

Script sẽ đọc tham số kết nối từ `config/test.properties` và kiểm tra dependency `python-proton`.

## 8) Khắc phục sự cố thường gặp
- Không kết nối được broker:
  - Kiểm tra `swim.broker.host`, `swim.broker.port`, thông tin xác thực, VPN/vhost
  - Kiểm tra broker có mở cổng và policy cho phép kết nối AMQP/JCSMP
- Chạy được nhưng hành vi “Broker Profile” không như mong đợi:
  - Kiểm tra key cấu hình liên quan (có thể tồn tại khác biệt giữa `amqp_broker_profile` và `amqp.broker.profile`)
- Báo cáo không xuất được:
  - Kiểm tra quyền ghi file tại thư mục đích
  - Thử chọn đường dẫn khác khi hộp thoại “Save Test Report” xuất hiện

