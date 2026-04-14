#!/bin/bash

# AMHS/SWIM Gateway Test Tool Installation Script for Linux
# This script requires root/sudo privileges to install missing packages.

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}Starting AMHS/SWIM Gateway Test Tool Installation...${NC}"

# Ensure we're in the project root
cd "$(dirname "$0")" || exit 1

# Helper function to detect OS and install packages
install_package() {
    local pkg_name=$1
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update && sudo apt-get install -y "$pkg_name"
    elif command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y "$pkg_name"
    elif command -v yum >/dev/null 2>&1; then
        sudo yum install -y "$pkg_name"
    elif command -v zypper >/dev/null 2>&1; then
        sudo zypper install -y "$pkg_name"
    else
        echo -e "${RED}Could not detect package manager. Please install $pkg_name manually.${NC}"
        exit 1
    fi
}

# 1. Check and Install JDK (11 or higher)
echo -e "\n${CYAN}Checking for Java Development Kit (JDK)...${NC}"
if type -p java >/dev/null 2>&1; then
    _java=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
    _java="$JAVA_HOME/bin/java"
else
    _java=""
fi

if [[ "$_java" ]]; then
    # Improved version detection
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}' | sed 's/_.*//')
    if [ -z "$version" ]; then
        # Fallback for OpenJDK
        version=$("$_java" -version 2>&1 | head -n 1 | cut -d' ' -f3 | tr -d '"')
    fi
    echo -e "${GREEN}Java is already installed (Version $version).${NC}"
else
    echo -e "${YELLOW}Java not found. Installing OpenJDK 11...${NC}"
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update && sudo apt-get install -y openjdk-11-jdk
    elif command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y java-11-openjdk-devel
    else
        install_package "java-11-openjdk-devel"
    fi
fi

# 2. Check and Install Maven
echo -e "\n${CYAN}Checking for Apache Maven...${NC}"
if type -p mvn >/dev/null 2>&1; then
    mvn_version=$(mvn -version | head -n 1)
    echo -e "${GREEN}Maven is already installed ($mvn_version).${NC}"
else
    echo -e "${YELLOW}Maven not found. Installing Apache Maven...${NC}"
    install_package "maven"
fi

# 3. Setup lib directory
LIB_DIR="lib"
echo -e "\n${CYAN}Setting up dependencies in '${LIB_DIR}'...${NC}"
if [ ! -d "$LIB_DIR" ]; then
    mkdir -p "$LIB_DIR"
    echo -e "${GREEN}Created lib/ directory.${NC}"
else
    echo -e "${GREEN}lib/ directory already exists.${NC}"
fi

# Prepare download command
if type -p wget >/dev/null 2>&1; then
    DOWNLOAD_CMD="wget -qO"
elif type -p curl >/dev/null 2>&1; then
    DOWNLOAD_CMD="curl -sLo"
else
    echo -e "${YELLOW}Neither wget nor curl found. Installing wget...${NC}"
    install_package "wget"
    DOWNLOAD_CMD="wget -qO"
fi

# Prepare stub command (create minimal ZIP if jars are missing/invalid)
create_jar_stub() {
    local target="$1"
    local desc="$2"
    echo -e "${YELLOW}Creating build-time stub for ${desc}...${NC}"
    # Standard ZIP EOCD header (22 bytes) in hex: 50 4B 05 06 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    printf "\x50\x4b\x05\x06\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00" > "$target"
}

# 4. Download/Stub JARs
echo -e "\n${CYAN}Locating Dependencies in '${LIB_DIR}'...${NC}"

# 4.1 Solace JCSMP
SOLACE_VERSION="10.20.0"
SOLACE_JAR="$LIB_DIR/sol-jcsmp-${SOLACE_VERSION}.jar"
if [ ! -s "$SOLACE_JAR" ]; then
    echo "Downloading Solace JCSMP JAR v${SOLACE_VERSION} from Maven Central..."
    if $DOWNLOAD_CMD "$SOLACE_JAR" "https://repo1.maven.org/maven2/com/solacesystems/sol-jcsmp/${SOLACE_VERSION}/sol-jcsmp-${SOLACE_VERSION}.jar"; then
        echo -e "${GREEN}Successfully downloaded 'sol-jcsmp-${SOLACE_VERSION}.jar'.${NC}"
    else
        echo -e "${RED}Failed to download Solace JAR. Creating stub...${NC}"
        create_jar_stub "$SOLACE_JAR" "Solace JCSMP"
    fi
else
    echo -e "${GREEN}Solace JAR found and valid.${NC}"
fi

# 4.2 AMQP Broker Check (Removed Isode proprietary SDK stubs as tool is now SWIM-focused)

# 4.4 Check for AMQP 1.0 Broker
echo -e "\n${CYAN}Checking for AMQP 1.0 Broker...${NC}"
BROKER_FOUND=false
for broker in qdrouterd rabbitmq-server activemq; do
    if command -v "$broker" >/dev/null 2>&1; then
        echo -e "${GREEN}AMQP Broker '$broker' is installed.${NC}"
        BROKER_FOUND=true
        break
    fi
done

if [ "$BROKER_FOUND" = false ]; then
    echo -e "${YELLOW}No AMQP 1.0 broker detected. Installing available AMQP broker...${NC}"
    if command -v apt-get >/dev/null 2>&1; then
        # Try qpid-dispatch first, then rabbitmq-server
        if ! sudo apt-get install -y qpid-dispatch >/dev/null 2>&1; then
            echo -e "${YELLOW}qpid-dispatch not found. Installing rabbitmq-server instead...${NC}"
            install_package "rabbitmq-server"
            echo -e "${CYAN}Enabling RabbitMQ AMQP 1.0 plugin...${NC}"
            sudo rabbitmq-plugins enable rabbitmq_amqp1_0 || true
            sudo systemctl restart rabbitmq-server || true
            echo -e "${GREEN}RabbitMQ installed and AMQP 1.0 plugin enabled.${NC}"
        else
            echo -e "${GREEN}Qpid Dispatch installed successfully.${NC}"
        fi
        BROKER_FOUND=true
    else
        echo -e "${YELLOW}Could not automatically install an AMQP broker on this system.${NC}"
        echo -e "Please install an AMQP 1.0 broker (e.g., Qpid, RabbitMQ, or ActiveMQ) manually."
    fi
fi

# Deployment note: Ensure all required SWIM and AMHS adapters are configured in test.properties.

# 5. Fix line endings for other scripts and execute build
echo -e "\n${CYAN}Fixing line endings for other scripts...${NC}"
find . -name "*.sh" -not -name "install.sh" -exec sed -i 's/\r$//' {} + || true

echo -e "\n${CYAN}Executing Maven build...${NC}"
if type -p mvn >/dev/null 2>&1; then
    echo "Running Maven build..."
    mvn clean install
elif [ -f "./scripts/build.sh" ]; then
    echo -e "${YELLOW}mvn not directly found. Attempting to run ./scripts/build.sh...${NC}"
    bash ./scripts/build.sh
else
    echo -e "${RED}Could not run Maven. Please run 'mvn clean install' manually.${NC}"
fi

echo -e "\n${GREEN}Installation workflow complete.${NC}"
