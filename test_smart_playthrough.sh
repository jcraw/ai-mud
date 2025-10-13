#!/bin/bash
# Test the Smart Playthrough scenario - player uses social skills to avoid combat

echo "Running Smart Playthrough Test..."
echo "Expected: Player uses persuasion/intimidation to bypass combat and complete dungeon"
echo ""

# Run test with scenario 11 (Smart Playthrough)
echo "1" | gradle :testbot:run --console=plain 2>&1 | tee test-logs/smart_playthrough_latest.log << EOF
1
11
EOF

echo ""
echo "Check test-logs/ directory for detailed results"
