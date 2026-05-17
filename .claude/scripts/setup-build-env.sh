#!/usr/bin/env bash
# Provisions the build environment for ha-track: Temurin JDK 25 (the ADR
# mandates Java 25) plus trust for the egress-proxy CAs so Maven can reach
# Maven Central over TLS. Idempotent: safe to run on every session start.
set -euo pipefail

JDK_DIR=/opt/jdk-25
JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.3%2B9/OpenJDK25U-jdk_x64_linux_hotspot_25.0.3_9.tar.gz"

if [ ! -x "$JDK_DIR/bin/java" ]; then
  echo "Installing Temurin JDK 25 to $JDK_DIR ..."
  tmp=$(mktemp -d)
  curl -sL --retry 4 --retry-delay 2 --max-time 300 -o "$tmp/jdk.tar.gz" "$JDK_URL"
  tar xzf "$tmp/jdk.tar.gz" -C "$tmp"
  mv "$tmp"/jdk-25* "$JDK_DIR"
  rm -rf "$tmp"
fi

# Trust the egress-proxy CAs in the JDK truststore (Maven uses Java TLS).
for crt in /usr/local/share/ca-certificates/*.crt; do
  [ -e "$crt" ] || continue
  alias=$(basename "$crt" .crt)
  if ! "$JDK_DIR/bin/keytool" -list -alias "$alias" \
        -keystore "$JDK_DIR/lib/security/cacerts" -storepass changeit >/dev/null 2>&1; then
    "$JDK_DIR/bin/keytool" -importcert -noprompt -trustcacerts \
      -alias "$alias" -file "$crt" \
      -keystore "$JDK_DIR/lib/security/cacerts" -storepass changeit >/dev/null
  fi
done

# Make JDK 25 the default for interactive shells in this session.
profile="$HOME/.bashrc"
marker="# ha-track JDK 25"
if ! grep -qF "$marker" "$profile" 2>/dev/null; then
  {
    echo ""
    echo "$marker"
    echo "export JAVA_HOME=$JDK_DIR"
    echo 'export PATH=$JAVA_HOME/bin:$PATH'
  } >> "$profile"
fi

echo "Build environment ready: $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
