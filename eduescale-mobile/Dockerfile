# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS android-build

ARG ANDROID_CMDLINE_TOOLS_BUILD=16111833
ARG ANDROID_CMDLINE_TOOLS_SHA1=e025545c62a8e64c7559119566a569fb1dec5f60

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/root/.gradle \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/36.0.0:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl --fail --location --retry 3 \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_BUILD}_latest.zip" \
        --output /tmp/android-command-line-tools.zip \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA1}  /tmp/android-command-line-tools.zip" | sha1sum --check --status \
    && unzip -q /tmp/android-command-line-tools.zip -d /tmp/android-command-line-tools \
    && mv /tmp/android-command-line-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -rf /tmp/android-command-line-tools /tmp/android-command-line-tools.zip

RUN yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses

RUN android --sdk="${ANDROID_SDK_ROOT}" sdk install platforms/android-36 build-tools/36.0.0

WORKDIR /workspace
ARG SOURCE_ROOT=.
COPY ${SOURCE_ROOT}/. .

RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=release_keystore,target=/run/secrets/release.jks,required=false \
    --mount=type=secret,id=keystore_properties,target=/run/secrets/keystore.properties,required=false \
    chmod +x ./gradlew \
    && if [ -s /run/secrets/release.jks ] && [ -s /run/secrets/keystore.properties ]; then \
        export EDUESCALE_KEYSTORE_PROPERTIES=/run/secrets/keystore.properties; \
    elif [ -s /run/secrets/release.jks ] || [ -s /run/secrets/keystore.properties ]; then \
        echo "Se requieren tanto release.jks como keystore.properties para firmar." >&2; \
        exit 1; \
    fi \
    && ./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease

FROM scratch AS apk
COPY --from=android-build /workspace/app/build/outputs/apk/debug/app-debug.apk /EduEscale-debug.apk

FROM scratch AS release-apk
COPY --from=android-build /workspace/app/build/outputs/apk/release/app-release-unsigned.apk /EduEscale-release-unsigned.apk

FROM scratch AS release
COPY --from=android-build /workspace/app/build/outputs/bundle/release/app-release.aab /EduEscale-release.aab
