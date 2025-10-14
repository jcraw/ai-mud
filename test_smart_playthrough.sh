#!/bin/bash
# Test the Smart Playthrough scenario - player uses social skills to avoid combat

echo "Running Smart Playthrough Test..."
echo "Expected: Player uses persuasion/intimidation to bypass combat and complete dungeon"
echo ""

# Create test-logs directory if it doesn't exist
mkdir -p test-logs

# Run test with scenario 11 (Smart Playthrough)
# Input: 1 (single-player mode), 11 (Smart Playthrough scenario)
gradle :testbot:run --console=plain << EOF
1
11
EOF

# Copy logs from testbot/test-logs to test-logs/
echo ""
echo "Copying logs from testbot/test-logs/..."
cp testbot/test-logs/smart_playthrough_*.txt test-logs/smart_playthrough_latest.txt 2>/dev/null || \
    echo "Warning: No smart_playthrough logs found in testbot/test-logs/"
cp testbot/test-logs/smart_playthrough_*_summary.txt test-logs/smart_playthrough_latest_summary.txt 2>/dev/null
cp testbot/test-logs/smart_playthrough_*_report.json test-logs/smart_playthrough_latest_report.json 2>/dev/null

echo ""
echo "Check test-logs/ directory for detailed results"
