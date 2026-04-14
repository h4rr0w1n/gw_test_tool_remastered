# Công cụ Kiểm thử AMHS/SWIM Gateway
**Bộ Công cụ Xác thực Tính tuân thủ theo Tài liệu ICAO EUR Doc 047 Phụ lục A**

---

## 🏗️ Tổng quan
**Bộ Công cụ Kiểm thử AMHS/SWIM Gateway** là một khung (framework) chẩn đoán và xác thực chuyên dụng, được thiết kế để kiểm tra sự tương thích và tính tuân thủ của các hệ thống Gateway giữa mạng AMHS và SWIM. Công cụ này thực thi nghiêm ngặt các quy trình và kịch bản kiểm thử được quy định trong tài liệu **ICAO EUR Doc 047, Phụ lục A (AMHS-SWIM Gateway Testing Plan)**.

Hệ thống được thiết kế dưới dạng một bộ phun tải (payload injector) có kiểm soát, cho phép các kỹ sư và điều hành viên kiểm tra logic chuyển đổi, ánh xạ giao thức và các ràng buộc hệ thống trên cả môi trường Solace (JCSMP) và AMQP 1.0 tiêu chuẩn (Proton-J).

---

## 🛠️ Các Tính năng Kỹ thuật Chính
- **Kiến trúc Adapter Kép**: Hỗ trợ đồng thời cả giao thức độc quyền Solace JCSMP (thông qua `SolaceSwimAdapter`) và chuẩn quốc tế AMQP 1.0 (thông qua `QpidSwimAdapter`).
- **Bộ Kiểm thử Tiêu chuẩn ICAO**: Tích hợp sẵn 16 kịch bản kiểm thử (CTSW101–CTSW116) bao gồm chuyển đổi văn bản/nhị phân, ánh xạ Subject/OHI, chuyển đổi mức ưu tiên (Priority) và xử lý thông báo (RN/NRN/NDR).
- **Ghi nhật ký Chi tiết (Deep Inspection)**: Cung cấp khả năng kiểm soát chi tiết các AMQP Header và Application Properties, phục vụ công tác đối soát thủ công tại vị trí điều khiển AMHS.
- **Báo cáo Kết quả Phiên làm việc**: Tự động xuất kết quả thực thi ra định dạng Excel (.xlsx) tiêu chuẩn, hỗ trợ công tác lập hồ sơ nghiệm thu và kiểm toán.

---

## 🚀 Cài đặt và Biên dịch
### Yêu cầu Tiên quyết
- **Java**: JDK 11 trở lên.
- **Maven**: Phiên bản 3.6.3 trở lên.
- **Kết nối Broker**: Có quyền truy cập mạng tới Solace VMR hoặc các AMQP 1.0 Broker tiêu chuẩn.

### Biên dịch từ Nguồn
Sử dụng kịch bản cài đặt tự động để kiểm tra môi trường và biên dịch:
```bash
chmod +x install.sh
./install.sh
```
Hoặc biên dịch thủ công bằng Maven:
```bash
mvn clean package
```
Sau khi biên dịch thành công, tệp JAR thực thi sẽ được tạo trong thư mục `target/`: `target/amhs-swim-test-tool-1.1.0.jar`.

---

## ⚙️ Cấu hình Hệ thống
Hệ thống được cấu hình thông qua tệp `config/test.properties`.

| Thuộc tính | Mô tả |
| :--- | :--- |
| `swim.broker.host` | Địa chỉ Host hoặc IP của SWIM Message Broker. |
| `swim.broker.port` | Cổng kết nối (5672 cho AMQP hoặc 55555 cho Solace SMF). |
| `amqp_broker_profile` | Chế độ thực thi: `SOLACE` hoặc `STANDARD`. |
| `gateway.test_recipient` | Địa chỉ O/R (AF-Address) mặc định cho các bài thử nghiệm chuyển đổi. |
| `gateway.trace_enabled` | Kích hoạt/Vô hiệu hóa tính năng hiển thị chi tiết Header trong Log. |

---

## 📗 Hướng dẫn Thực thi Kiểm thử
### Các bước thực hiện:
1. **Khởi chạy Giao diện**:
   Sử dụng kịch bản khởi chạy nhanh:
   ```bash
   chmod +x run_tool.sh
   ./run_tool.sh
   ```
   Hoặc khởi chạy trực tiếp bằng Java:
   ```bash
   java -jar target/amhs-swim-test-tool-1.1.0.jar
   ```
2. **Kiểm tra Kết nối**: Xác thực trạng thái kết nối với Broker trong tab **Settings**.
3. **Lựa chọn Kịch bản**: Chọn bài kiểm thử (ví dụ: CTSW101) từ danh sách bên trái.
4. **Nhập Tham số**: Tùy chỉnh tải tin (payload) hoặc mức ưu tiên nếu kịch bản yêu cầu.
5. **Thực thi**: Nhấn nút **Execute** (hoặc Batch Execute) để thực hiện phun tin.
6. **Xác thực Kết quả**: Theo dõi nhật ký chi tiết trong giao diện công cụ và đối soát với kết quả nhận được tại giao diện điều khiển của hệ thống AMHS.
7. **Xuất Báo cáo**: Sử dụng tính năng **Export Report** để lưu trữ kết quả phục vụ báo cáo kỹ thuật.

---

## ⚖️ Tuyên bố Tuân thủ
*Công cụ này được thiết kế để sử dụng trong môi trường thử nghiệm (Sandbox/Pre-production) nhằm xác thực logic của Gateway. Kết quả từ công cụ này không thay thế cho các quy trình chứng nhận hệ thống (Certification) chính thức.*

---
© 2026 Đội ngũ Phát triển Khung Kiểm thử AMHS/SWIM Gateway.