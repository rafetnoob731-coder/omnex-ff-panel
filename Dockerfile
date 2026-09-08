# Omnex FF Panel - Cloud Build Image
# Usage: docker build -t omnex-ff-builder . && docker run -v $(pwd):/workspace omnex-ff-builder

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_HOME=/opt/android-ndk
ENV JAVA_HOME=/opt/java
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_NDK_HOME}:${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${PATH}"

WORKDIR /workspace

# Install Java 17, Android tools, NDK, CMake, build essentials
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    wget \
    unzip \
    curl \
    git \
    python3 \
    python3-pip \
    lib32stdc++6 \
    lib32z1 \
    libstdc++6 \
    libz-dev \
    libc6-dev \
    libc6-i386 \
    libtinfo5 \
    build-essential \
    zip \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK Command Line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools/tmp && \
    mv ${ANDROID_HOME}/cmdline-tools/tmp/cmdline-tools/* ${ANDROID_HOME}/cmdline-tools/ && \
    rm -rf /tmp/cmdline-tools.zip

# Install Android SDK Platform 34 and Build Tools 34.0.0
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "cmake;3.22.1" && \
    sdkmanager --licenses > /dev/null 2>&1

# Install Android NDK r25.2.9519653
RUN sdkmanager "ndk;25.2.9519653" && \
    ln -sf ${ANDROID_HOME}/ndk/25.2.9519653 ${ANDROID_NDK_HOME}

# Install Gradle 8.2
RUN wget -q https://services.gradle.org/distributions/gradle-8.2-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    mv /opt/gradle-8.2 ${GRADLE_HOME} && \
    rm /tmp/gradle.zip

# Create wrapper script for sdkmanager
RUN echo '#!/bin/bash' > /usr/local/bin/sdkmanager && \
    echo '${ANDROID_HOME}/cmdline-tools/bin/sdkmanager "$@"' >> /usr/local/bin/sdkmanager && \
    chmod +x /usr/local/bin/sdkmanager

# Verify installations
RUN java -version && \
    sdkmanager --version && \
    gradle --version && \
    cmake --version

# Copy project files
COPY . /workspace/

# Set permissions
RUN chmod +x gradlew && \
    chmod +x gradle/wrapper/gradle-wrapper.jar

# Default command: build debug APK
CMD ["./gradlew", "assembleDebug", "--no-daemon", "--stacktrace"]