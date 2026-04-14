# Ánh xạ API Sử Dụng Trong Tool

## 1. Phía AMHS (X.400)
Sử dụng Isode X.400 Gateway API và Client API (thông qua JNI Wrapper).

| Chức năng | API C (Isode) | Java Wrapper Method | Ghi chú |
| :--- | :--- | :--- | :--- |
| Mở session MTA | `X400mtOpen` | `AmhsDriver.openSession()` | Dùng cho gửi message (Gateway) |
| Tạo message | `X400mtMsgNew` | `AmhsDriver.createMessage()` | |
| Thêm tham số | `X400mtMsgAddStrParam` | `Message.addParameter()` | Thêm O/R Address, Subject... |
| Gửi message | `X400mtMsgSend` | `AmhsDriver.sendMessage()` | |
| Nhận message | `X400msMsgGetStart` | `AmhsDriver.receiveMessage()` | Dùng cho phía Client/UA |
| Kết thúc nhận | `X400msMsgGetFinish` | `AmhsDriver.finishReceive()` | Báo cáo DR/NDR |

## 2. Phía SWIM (AMQP 1.0 / REST)
**Tuân thủ ICAO EUR Doc 047, AMHS-SWIM Gateway Testing Plan V3.0**

### 2.1 AMQP 1.0 Messaging
Sử dụng Apache Qpid Proton-J cho AMQP 1.0 standard (không dùng Solace proprietary API).

| Chức năng | AMQP 1.0 API | Java Class/Method | Ghi chú |
| :--- | :--- | :--- | :--- |
| Kết nối Broker | `Proton.connection()` | `SwimDriver.connect()` | AMQP 1.0 over TCP (5672) hoặc TLS (5671) |
| Tạo Sender Link | `session.sender(address)` | `SwimDriver.sendAmqpMessage()` | Dùng cho SWIM → AMHS |
| Tạo Receiver Link | `session.receiver(address)` | `SwimDriver.consumeMessage()` | Dùng cho AMHS → SWIM |
| Tạo Message | `Proton.message()` | `SwimDriver.publishMessage()` | |
| Set Properties | `message.setApplicationProperties()` | `SwimDriver.AMQPProperties` | amhs_ats_pri, amhs_recipients, etc. |
| Set Body (binary) | `new Data(new Binary(payload))` | `createBodySection()` | Cho binary content |
| Set Body (text) | `new AmqpValue(text)` | `createBodySection()` | Cho ia5-text, utf8-text |
| Send Message | `sender.send(data)` | `sendAmqpMessage()` | Với delivery settlement |
| Receive Message | `receiver.receive(timeout)` | `receiveAmqpMessage()` | Blocking với timeout |

### 2.2 AMQP 1.0 Application Properties (theo EUR Doc 047)

| Property Key | Symbol | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- | :--- |
| amhs_ats_pri | `AMHS_ATS_PRI` | String | ATS Priority: SS/DD/FF/GG/KK. |
| amhs_recipients | `AMHS_RECIPIENTS` | String | Danh sách recipients (AFTN/O/R addresses), phân tách bằng dấu phẩy. |
| amhs_bodypart_type | `AMHS_BODY_PART_TYPE` | String | Kiểu body part: ia5-text, utf8-text, general-text-body-part, etc. |
| amhs_content_type | `AMHS_CONTENT_TYPE` | String | Content type identifier (ví dụ: text/plain; charset=utf-8). |
| amhs_originator | `AMHS_ORIGINATOR` | String | Địa chỉ người gửi (Originator address). |
| amhs_subject | `AMHS_SUBJECT` | String | Chủ đề tin nhắn (Message subject). |
| amhs_message_id | `AMHS_MESSAGE_ID` | String | Unique message identifier. |
| amhs_filing_time | `AMHS_FILING_TIME` | String | Filing time timestamp (YYMMDDhhmm). |
| amhs_ats_ohi | `AMHS_ATS_OHI` | String | Originator-Handled-Identifier (Truncated 48/53 bytes). |
| amhs_notification_request | - | String | Yêu cầu thông báo: rn, nrn. |
| amhs_content_encoding | - | String | Encoding cho body part: IA5, ISO-8859-1, ISO-646. |
| amhs_ftbp_file_name | - | String | Tên file cho File Transfer Body Part (FTBP). |
| amhs_ftbp_object_size | - | String | Kích thước đối tượng FTBP. |
| swim_compression | - | String | Kiểu nén dữ liệu: gzip. |
| amhs_dl_history | `AMHS_DL_HISTORY` | String | Lịch sử danh sách phân phối (Distribution list history). |
| amhs_sec_envelope | `AMHS_SEC_ENVELOPE` | Binary | Security envelope cho tin nhắn có chữ ký. |

### 2.3 REST API Authentication & Service Discovery
Sử dụng Java HttpURLConnection cho REST calls.

| Chức năng | REST Endpoint | Java Method | Ghi chú |
| :--- | :--- | :--- | :--- |
| Lấy JWT Token | `POST /realms/{realm}/protocol/openid-connect/token` | `SwimDriver.obtainTokenFromKeycloak()` | Keycloak OIDC OAuth2 |
| Lookup Service | `GET /services?name={name}&type={type}` | `SwimDriver.lookupServiceInRegistry()` | SWIM Service Registry |
| Auth Header | `Authorization: Bearer {token}` | Tự động attach | Cho các REST calls cần auth |

## 3. Directory (Danh bạ)
Sử dụng Isode DSAPI và ATNDS API (thông qua JNI Wrapper).

| Chức năng | API C (Isode) | Java Wrapper Method | Ghi chú |
| :--- | :--- | :--- | :--- |
| Bind Directory | `DS_BindSimpleSync` | `DirectoryDriver.bind()` | Kết nối LDAP/DAP |
| Tìm kiếm | `DS_SearchSync` | `DirectoryDriver.search()` | Tìm O/R Address |
| Chuyển đổi AFTN | `ATNds_AFTN2AMHS` | `DirectoryDriver.convertAftnToAmhs()` | |
| Chuyển đổi AMHS | `ATNds_AMHS2AFTN` | `DirectoryDriver.convertAmhsToAftn()` | |

## 4. AMHS Priority ↔ AMQP Priority Mapping

| AMHS Priority | AMQP 1.0 Priority (0-9) | Ý nghĩa |
| :--- | :--- | :--- |
| SS (Urgent) | 6 | Khẩn cấp |
| DD (Non-Urgent) | 4 | Không khẩn cấp |
| FF (Normal) | 0 | Bình thường |
| GG (Lower) | 0 | Thấp hơn |
| KK (Lowest) | 0 | Thấp nhất |