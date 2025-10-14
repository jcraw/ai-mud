#!/bin/bash
# Test the Bad Playthrough scenario - player should die to boss without gear

echo "Running Bad Playthrough Test..."
echo "Expected: Player rushes to throne room and dies to Skeleton King"
echo ""

# Create test-logs directory if it doesn't exist
mkdir -p test-logs

# Run test with scenario 9 (Bad Playthrough)
# Input: 1 (single-player mode), 9 (Bad Playthrough scenario)
gradle :testbot:run --console=plain << EOF
1
9
EOF

# Copy logs from testbot/test-logs to test-logs/
echo ""
echo "Copying logs from testbot/test-logs/..."
cp testbot/test-logs/bad_playthrough_*.txt test-logs/bad_playthrough_latest.txt 2>/dev/null || \
    echo "Warning: No bad_playthrough logs found in testbot/test-logs/"
cp testbot/test-logs/bad_playthrough_*_summary.txt test-logs/bad_playthrough_latest_summary.txt 2>/dev/null
cp testbot/test-logs/bad_playthrough_*_report.json test-logs/bad_playthrough_latest_report.json 2>/dev/null

echo ""
echo "Check test-logs/ directory for detailed results"
