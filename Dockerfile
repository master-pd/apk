FROM openjdk:17-jdk-slim

# Install Android SDK
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV ANDROID_HOME /opt/android-sdk
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools

# Download Android SDK
RUN mkdir -p ${ANDROID_HOME} && cd ${ANDROID_HOME} && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O commandlinetools.zip && \
    unzip commandlinetools.zip && \
    rm commandlinetools.zip

# Accept licenses
RUN yes | ${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --licenses

# Install build tools and platform
RUN ${ANDROID_HOME}/cmdline-tools/bin/sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "platform-tools"

WORKDIR /app

# Copy project files
COPY . .

# Build APK
RUN chmod +x gradlew
RUN ./gradlew assembleDebug

CMD ["cp", "app/build/outputs/apk/debug/app-debug.apk", "/output/"]
