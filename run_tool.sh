#!/bin/bash

# AMHS/SWIM Gateway Test Tool Runner Script for Linux

# Ensure we're in the project root
cd "$(dirname "$0")" || exit 1

# Check for Java
if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java is not installed or not in PATH."
    exit 1
fi

# Check if target exist, if not try to build
if [ ! -d "target/classes" ]; then
    echo "Info: 'target/classes' not found. Attempting to compile..."
    mvn compile
    if [ $? -ne 0 ]; then
        echo "Error: Compilation failed. Please fix environment issues (e.g. 'sudo rm -rf target')."
        exit 1
    fi
fi

echo "Setting up classpath..."

# Get Maven dependencies classpath
CP=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)

# Add project classes and local lib jars
CP="target/classes:lib/*:$CP"

echo "Starting AMHS/SWIM Gateway Test Tool..."
java -cp "$CP" com.amhs.swim.test.Main "$@"
