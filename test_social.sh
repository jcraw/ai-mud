#!/bin/bash

# Test script for social interactions powered by LLM dialogue.
# Runs the focused unit test that exercises NPC conversations.

set -euo pipefail

cat <<'INTRO'
Testing social interaction dialogue...

Plan:
1. Create a single-room test world with the Old Guard on duty
2. Add a player session in that room
3. Talk to the Old Guard and capture the LLM-generated dialogue

Running focused Gradle test:
INTRO

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle}" ./gradlew --console=plain :reasoning:test --tests com.jcraw.mud.reasoning.SocialInteractionTest "$@" | tee test-logs/social_test_output.log

if [ ${PIPESTATUS[0]} -eq 0 ]; then
  echo "
Social interaction test passed. See test-logs/social_test_output.log for details."
else
  echo "
Social interaction test failed. Review test-logs/social_test_output.log for errors." >&2
  exit 1
fi
