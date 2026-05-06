#!/bin/bash

# CTSW116 Payload Verification Runner
# This script runs the standalone AMQP consumer to verify GZIP payload delivery.

# 1. Check for Python
if ! command -v python3 &> /dev/null; then
    if ! command -v python &> /dev/null; then
        echo "Error: Python is not installed. Please install Python 3."
        exit 1
    else
        PYTHON_CMD="python"
    fi
else
    PYTHON_CMD="python3"
fi

# 2. Check for python-proton library
$PYTHON_CMD -c "import proton" &> /dev/null
if [ $? -ne 0 ]; then
    echo "Error: 'python-proton' library not found."
    echo "Please install it using: pip install python-proton"
    exit 1
fi

# 3. Run the verifier
# It will automatically pick up settings from config/test.properties
echo "[*] Starting Standalone AMQP Verifier..."
$PYTHON_CMD verify_ctsw116_consumer.py "$@"

# 4. Check if payload was received
if [ -f "ctsw116_payload_received.gz" ]; then
    echo ""
    echo "[+] SUCCESS: Payload file 'ctsw116_payload_received.gz' found."
    echo "[*] Checking GZIP integrity..."
    if command -v gzip &> /dev/null; then
        gzip -t ctsw116_payload_received.gz
        if [ $? -eq 0 ]; then
            echo "[+] GZIP Integrity Check: PASSED"
        else
            echo "[-] GZIP Integrity Check: FAILED (Malformed payload)"
        fi
    else
        echo "[!] 'gzip' command not found, skipping automatic integrity check."
    fi
else
    echo ""
    echo "[-] No payload file found. Ensure the test case is running and sending to the correct queue."
fi
