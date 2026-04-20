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

## 🚀 Cài đặt

### Yêu cầu Tiên quyết
- **Java**: JDK 11 trở lên.
- **Maven**: Phiên bản 3.6.3 trở lên.
- **Kết nối Broker**: Có quyền truy cập mạng tới Solace VMR, RabbitMQ, Azure Service Bus, IBM MQ, hoặc các AMQP 1.0 Broker tiêu chuẩn.

### Linux/macOS

#### Biên dịch từ Nguồn
Sử dụng kịch bản cài đặt tự động để kiểm tra môi trường và biên dịch:
```bash
chmod +x install.sh
./install.sh
```
Hoặc biên dịch thủ công bằng Maven:
```bash
mvn clean package
```

Sau khi biên dịch thành công, tệp JAR thực thi sẽ được tạo trong thư mục `target/`:
- `target/test-tool-1.0.0-jar-with-dependencies.jar` (JAR bao gồm tất cả dependencies - khuyến nghị)
- `target/test-tool-1.0.0.jar` (JAR chính, yêu cầu classpath riêng)

#### Chạy công cụ
Sử dụng kịch bản khởi chạy nhanh:
```bash
chmod +x run_tool.sh
./run_tool.sh
```
Hoặc khởi chạy trực tiếp bằng Java:
```bash
java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
```

---

### Windows

#### Yêu cầu Windows:
1. **Java JDK 11+**: Tải và cài đặt từ [Oracle](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html) hoặc [Adoptium](https://adoptium.net/)
2. **Maven 3.6+**: Tải từ [Apache Maven](https://maven.apache.org/download.cgi) và cấu hình biến môi trường `PATH`
3. **Git Bash** (khuyến nghị): Để chạy các script shell hoặc sử dụng Command Prompt/PowerShell

#### Các bước cài đặt Windows:
1. Mở Command Prompt hoặc PowerShell tại thư mục dự án
2. Biên dịch bằng Maven:
   ```cmd
   mvn clean package
   ```
3. Chạy công cụ:
   ```cmd
   java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
   ```
   
Hoặc tạo file batch `run_tool.bat`:
```batch
@echo off
cd /d %~dp0
java -jar target/test-tool-1.0.0-jar-with-dependencies.jar %*
```

#### Lưu ý Windows:
- Đảm bảo biến môi trường `JAVA_HOME` và `MAVEN_HOME` được thiết lập đúng
- Nếu gặp lỗi độ dài đường dẫn, enable Long Paths trong Windows Registry
- Script `install.sh` và `run_tool.sh` chỉ dành cho Linux/macOS; người dùng Windows cần sử dụng Command Prompt/PowerShell hoặc Git Bash

---

## ⚙️ Cấu hình Hệ thống

Hệ thống được cấu hình thông qua tệp `config/test.properties` nằm trong thư mục gốc **HOẶC** thông qua giao diện Settings trong công cụ.

### Cấu hình qua File

| Thuộc tính | Mô tả | Giá trị mẫu |
| :--- | :--- | :--- |
| `swim.broker.host` | Địa chỉ Host hoặc IP của SWIM Message Broker | `localhost` |
| `swim.broker.port` | Cổng kết nối (5672 cho AMQP, 55555 cho Solace SMF) | `5672` |
| `swim.broker.user` | Tên người dùng xác thực broker | `default` |
| `swim.broker.password` | Mật khẩu xác thực broker | `default` |
| `swim.broker.vpn` | Tên VPN (cho Solace) hoặc Virtual Host (cho RabbitMQ) | `default` |
| `amqp.broker.profile` | Hồ sơ broker AMQP: `STANDARD`, `SOLACE`, `RABBITMQ`, `AZURE_SERVICE_BUS`, `IBM_MQ` | `STANDARD` |
| `gateway.default_topic` | Topic AMQP đích mặc định cho các bài thử nghiệm | `TEST.TOPIC` |
| `gateway.default_queue` | Queue AMQP đích mặc định | `TEST.QUEUE` |
| `gateway.default_originator` | Mã định danh Originator mặc định (AF-Address) | `LFRCZZZZ` |
| `amhs.mta.host` | Địa chỉ host của AMHS MTA | `localhost` |
| `amhs.mta.port` | Cổng kết nối AMHS MTA | `10000` |
| `directory.host` | Địa chỉ LDAP Directory Server | `ldap://localhost:389` |
| `directory.dn` | Distinguished Name cho LDAP | `cn=admin` |
| `directory.password` | Mật khẩu LDAP | `secret` |
| `gateway.max_recipients` | Số lượng recipient tối đa cho phép | `512` |
| `gateway.max_size` | Kích thước message tối đa (bytes) | `1000000` |

### Cấu hình qua Tool Settings (GUI)

Người dùng có thể cấu hình tất cả các tham số trên thông qua giao diện Settings mà không cần chỉnh sửa file thủ công:

1. **Mở Settings Dialog**: Nhấn vào biểu tượng bánh răng (⚙️) trên thanh công cụ hoặc chọn menu **Settings**

2. **Các tab cấu hình trong Settings**:
   - **SWIM Broker**: Cấu hình thông số kết nối broker (host, port, user, password, vpn)
   - **Broker Profile**: Chọn loại broker (STANDARD, SOLACE, RABBITMQ, AZURE_SERVICE_BUS, IBM_MQ)
   - **Gateway Defaults**: Thiết lập topic, queue, originator mặc định
   - **AMHS MTA**: Cấu hình thông số AMHS MTA (host, port)
   - **Directory/LDAP**: Cấu hình LDAP server (host, DN, password)
   - **Limits**: Thiết lập giới hạn recipients và kích thước message

3. **Lưu cấu hình**: 
   - Nhấn **Save** để lưu thay đổi vào file `config/test.properties`
   - Nhấn **Test Connection** để kiểm tra kết nối với broker trước khi lưu
   - Cấu hình sẽ tự động lưu khi đóng dialog nếu có thay đổi

4. **Khôi phục mặc định**: Nhấn **Reset to Defaults** để khôi phục cài đặt gốc từ file embedded trong JAR

### Cơ chế tải cấu hình:
1. **Mặc định**: Tải từ `src/main/resources/config/test.properties` (embedded trong JAR)
2. **Ghi đè**: Nếu tồn tại file `config/test.properties` bên ngoài, nó sẽ ghi đè các giá trị mặc định
3. **GUI**: Người dùng có thể thay đổi cấu hình qua tab Settings và lưu lại vào file

### Lưu ý quan trọng về cấu hình:
- Thuộc tính `swim.test.profile` trong file cấu hình cũ **không còn được sử dụng**. Thay vào đó, sử dụng `amqp.broker.profile`
- Cấu hình qua GUI và qua file hoàn toàn tương đương - thay đổi ở đâu cũng sẽ ảnh hưởng đến nơi còn lại
- Để reset về mặc định, xóa file `config/test.properties` bên ngoài hoặc nhấn **Reset to Defaults** trong Settings

---

## 📗 Hướng dẫn Thực thi Kiểm thử

### Các bước thực hiện:

1. **Khởi chạy Giao diện**:
   
   **Trên Linux/macOS:**
   ```bash
   ./run_tool.sh
   ```
   Hoặc khởi chạy trực tiếp bằng Java:
   ```bash
   java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
   ```
   
   **Trên Windows:**
   ```cmd
   java -jar target/test-tool-1.0.0-jar-with-dependencies.jar
   ```
   Hoặc sử dụng file batch (nếu đã tạo):
   ```cmd
   run_tool.bat
   ```

2. **Kiểm tra Kết nối**: Xác thực trạng thái kết nối với Broker trong tab **Settings** (biểu tượng bánh răng trên thanh công cụ).

3. **Lựa chọn Kịch bản**: Chọn bài kiểm thử (ví dụ: CTSW101) từ cây thư mục bên trái.

4. **Nhập Tham số**: Tùy chỉnh tải tin (payload), recipient, hoặc mức ưu tiên nếu kịch bản yêu cầu.

5. **Thực thi**: Nhấn nút **Execute** (hoặc **Batch Execute**) để thực hiện phun tin.

6. **Xác thực Kết quả**: Theo dõi nhật ký chi tiết trong giao diện công cụ và đối soát với kết quả nhận được tại giao diện điều khiển của hệ thống AMHS.

7. **Xuất Báo cáo**: Sử dụng tính năng **Export Report** (trong Settings dialog hoặc Results dialog) để lưu kết quả ra file Excel (.xlsx) phục vụ báo cáo kỹ thuật.

---
