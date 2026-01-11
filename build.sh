#!/bin/bash

echo "🚀 Building Auto Backup Pro APK..."

# Clean previous builds
./gradlew clean

# Build debug APK
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ APK built successfully!"
    echo "📁 APK location: app/build/outputs/apk/debug/app-debug.apk"
    
    # Copy to current directory
    cp app/build/outputs/apk/debug/app-debug.apk ./AutoBackupPro.apk
    echo "📋 Copied to: ./AutoBackupPro.apk"
    
    # Get file size
    FILE_SIZE=$(stat -f%z ./AutoBackupPro.apk 2>/dev/null || stat -c%s ./AutoBackupPro.apk)
    FILE_SIZE_MB=$(echo "scale=2; $FILE_SIZE/1024/1024" | bc)
    echo "📊 APK Size: ${FILE_SIZE_MB}MB"
    
else
    echo "❌ Build failed!"
    exit 1
fi
