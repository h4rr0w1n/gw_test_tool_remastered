#!/bin/bash
# Run script for AMHS/SWIM test tool
cd "$(dirname "$0")/.." || exit 1
mvn exec:java -Dexec.mainClass="com.amhs.swim.test.Main"
