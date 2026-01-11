#!/usr/bin/env bash

##############################################################################
##  Simplified Gradle wrapper script for GitHub Actions
##############################################################################

set -e  # Exit on error

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
GRADLE_WRAPPER_JAR="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"

# Check if wrapper jar exists
if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    echo "❌ Error: gradle-wrapper.jar not found at $GRADLE_WRAPPER_JAR"
    echo "📥 Downloading gradle-wrapper.jar..."
    
    # Create directory if it doesn't exist
    mkdir -p "$(dirname "$GRADLE_WRAPPER_JAR")"
    
    # Download gradle-wrapper.jar
    curl -L -o "$GRADLE_WRAPPER_JAR" \
        "https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar" \
        || wget -O "$GRADLE_WRAPPER_JAR" \
        "https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar"
    
    if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
        echo "❌ Failed to download gradle-wrapper.jar"
        exit 1
    fi
fi

# Set Java options
if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-Xmx64m -Xms64m"
fi

# Run Gradle
exec java $JAVA_OPTS -jar "$GRADLE_WRAPPER_JAR" "$@"
