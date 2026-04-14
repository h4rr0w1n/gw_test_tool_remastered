#!/bin/bash
# Build script for AMHS/SWIM test tool
cd "$(dirname "$0")/.." || exit 1
mvn clean install
