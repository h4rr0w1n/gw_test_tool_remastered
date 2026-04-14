
```bash
#!/bin/bash
# File: scripts/generate_keystore.sh
# Script hỗ trợ tạo keystore PKCS#12 cho AMHS/SWIM Gateway Test Tool.
# Tuân thủ chuẩn bảo mật AMHS SEC (EUR Doc 047).

# Cấu hình mặc định
KEYSTORE_PATH="../src/main/resources/security/keystore.p12"
ALIAS="gateway_identity"
KEYALG="RSA"
KEYSIZE=2048
STORETYPE="PKCS12"
VALIDITY=365
DNAME="CN=AMHS-SWIM-Gateway, OU=Test, O=TestOrg, C=GB"

# Kiểm tra thư mục đích
if [ ! -d "../src/main/resources/security" ]; then
    echo "Tạo thư mục bảo mật..."
    mkdir -p "../src/main/resources/security"
fi

# Kiểm tra keytool
if ! command -v keytool &> /dev/null; then
    echo "Lỗi: Không tìm thấy command 'keytool'. Vui lòng cài đặt Java JDK."
    exit 1
fi

# Thông báo
echo "Bắt đầu tạo keystore tại: $KEYSTORE_PATH"
echo "Alias: $ALIAS"
echo "Thuật toán: $KEYALG ($KEYSIZE)"

# Tạo keystore
# Lưu ý: Script sẽ yêu cầu người dùng nhập mật khẩu keystore và mật khẩu khóa riêng.
keytool -genkeypair \
    -alias "$ALIAS" \
    -keyalg "$KEYALG" \
    -keysize "$KEYSIZE" \
    -storetype "$STORETYPE" \
    -keystore "$KEYSTORE_PATH" \
    -validity "$VALIDITY" \
    -dname "$DNAME"

if [ $? -eq 0 ]; then
    echo "Thành công: Keystore đã được tạo tại $KEYSTORE_PATH"
    echo "Lưu ý: Hãy cập nhật mật khẩu trong file config/test.properties"
else
    echo "Lỗi: Không thể tạo keystore."
    exit 1
fi