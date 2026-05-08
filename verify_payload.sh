#!/bin/bash

# AMQP Universal Payload Verification Runner
# This script runs the standalone AMQP consumer to verify any payload delivery.

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
    echo "[!] 'proton' library not found."
    echo "[*] Attempting to install python3-qpid-proton..."
    
    if command -v apt-get &> /dev/null; then
        sudo apt-get update
        sudo apt-get install -y python3-qpid-proton
        if [ $? -ne 0 ]; then
            echo "[-] Failed to install via apt-get. Trying pip..."
            pip3 install python-qpid-proton || pip install python-qpid-proton
        fi
    elif command -v yum &> /dev/null; then
        sudo yum install -y python3-qpid-proton
    else
        echo "[*] Using pip to install python-qpid-proton..."
        pip3 install python-qpid-proton || pip install python-qpid-proton
    fi
    
    # Check again
    $PYTHON_CMD -c "import proton" &> /dev/null
    if [ $? -ne 0 ]; then
        echo "[-] Error: Failed to install proton library automatically."
        echo "Please install it manually: sudo apt install python3-qpid-proton OR pip install python-qpid-proton"
        exit 1
    fi
    echo "[+] proton library installed successfully."
fi

# 3. Run the verifier
# It will automatically pick up settings from config/test.properties
echo "[*] Starting Universal AMQP Verifier..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

$PYTHON_CMD verifier/verifying_consumer.py "$@"
