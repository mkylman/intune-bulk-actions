#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

set -euf

BASE_DIR=$(cd "$(dirname "$0")" && pwd)
MVNW_VERBOSE="${MVNW_VERBOSE:-false}"

if [ "$MVNW_VERBOSE" = "true" ]; then
  set -x
fi

WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
PROPS_FILE="$WRAPPER_DIR/maven-wrapper.properties"
JAR_FILE="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS_FILE" ]; then
  echo "Missing $PROPS_FILE" >&2
  exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
  WRAPPER_URL=$(grep -E '^wrapperUrl=' "$PROPS_FILE" | cut -d= -f2-)
  if [ -z "${WRAPPER_URL:-}" ]; then
    echo "wrapperUrl not set in $PROPS_FILE" >&2
    exit 1
  fi
  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Maven Wrapper from $WRAPPER_URL" >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$JAR_FILE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$JAR_FILE"
  else
    echo "Neither curl nor wget is available to download Maven Wrapper." >&2
    echo "Please download $WRAPPER_URL to $JAR_FILE" >&2
    exit 1
  fi
fi

JAVA_EXEC="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_EXEC" ]; then
  JAVA_EXEC="java"
fi

exec "$JAVA_EXEC" -classpath "$JAR_FILE" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" org.apache.maven.wrapper.MavenWrapperMain "$@"

