#!/bin/sh
# LeetVise – revise the LeetCode problems you've already solved.
#
# Copyright (c) 2026 Vishwajeet Pratap Singh
#
# Author:    Vishwajeet Pratap Singh
# GitHub:    https://github.com/vishwajeet-singhh
# LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
# Portfolio: https://vishwajeet.me
# Source:    https://github.com/vishwajeet-singhh/LeetVise

# Start LeetVise on macOS / Linux:  ./run.sh   (or: sh run.sh)
# Needs Java 21 or newer. Maven is downloaded automatically by ./mvnw.
cd "$(dirname "$0")" || exit 1

JAVA="java"
[ -n "$JAVA_HOME" ] && JAVA="$JAVA_HOME/bin/java"
if ! command -v "$JAVA" >/dev/null 2>&1; then
  echo "LeetVise needs Java 21 or newer. Install it (e.g. https://adoptium.net) and run this again." >&2
  exit 1
fi
major=$("$JAVA" -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print (v[1] == "1" ? v[2] : v[1]); exit }')
if [ -n "$major" ] && [ "$major" -lt 21 ] 2>/dev/null; then
  echo "LeetVise needs Java 21 or newer – you have Java $major. Install a newer one (e.g. https://adoptium.net)." >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env – add your LeetCode cookie there to load everything (see README)."
fi

echo "Starting LeetVise… (the first run downloads dependencies, give it a minute)"
exec sh ./mvnw -q spring-boot:run

# LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
