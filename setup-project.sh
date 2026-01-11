#!/bin/bash

echo "🚀 Setting up Auto Backup Pro Project"
echo "====================================="

# Create directory structure
mkdir -p AutoBackupPro
cd AutoBackupPro

echo "📁 Creating project structure..."

# Create root files
cat > settings.gradle << 'EOF'
rootProject.name = 'AutoBackupPro'
include ':app'
EOF

cat > build.gradle << 'EOF'
// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
EOF

# Create gradle wrapper
mkdir -p gradle/wrapper

cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# Create gradlew
cat > gradlew << 'EOF'
#!/usr/bin/env bash
APP_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
EOF

chmod +x gradlew

# Create gradlew.bat
cat > gradlew.bat << 'EOF'
@rem Gradle startup script for Windows
@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%
java -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
EOF

# Create app directory structure
mkdir -p app/src/main/java/com/personal/autobackup
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/drawable

# Create app build.gradle
cat > app/build.gradle << 'EOF'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.personal.autobackup'
    compileSdk 34

    defaultConfig {
        applicationId "com.personal.autobackup"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.10.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
EOF

# Create GitHub Actions workflow
mkdir -p .github/workflows

cat > .github/workflows/build-apk.yml << 'EOF'
name: Build APK

on: [push, workflow_dispatch]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Setup Java
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Make gradlew executable
      run: chmod +x gradlew
    
    - name: Build APK
      run: ./gradlew assembleDebug
    
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: AutoBackupPro
        path: app/build/outputs/apk/debug/*.apk
EOF

echo "✅ Project setup complete!"
echo ""
echo "📁 Project structure:"
find . -type f -name "*.gradle*" -o -name "*.yml" | sort

echo ""
echo "👉 Next steps:"
echo "1. Copy your source code to app/src/main/"
echo "2. Push to GitHub:"
echo "   git init"
echo "   git add ."
echo "   git commit -m 'Initial commit'"
echo "   git remote add origin YOUR_REPO_URL"
echo "   git push -u origin main"
