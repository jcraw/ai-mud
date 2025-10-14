#!/bin/bash
# Test the Brute Force Playthrough scenario - player collects gear and defeats boss

echo "Running Brute Force Playthrough Test..."
echo "Expected: Player collects best gear (Iron Sword +5, Chainmail +4) and defeats Skeleton King"
echo ""

# Create test-logs directory if it doesn't exist
mkdir -p test-logs

# Run test with scenario 10 (Brute Force Playthrough)
# Input: 1 (single-player mode), 10 (Brute Force Playthrough scenario)
gradle :testbot:run --console=plain << EOF
1
10
EOF

# Copy logs from testbot/test-logs to test-logs/
echo ""
echo "Copying logs from testbot/test-logs/..."
cp testbot/test-logs/brute_force_playthrough_*.txt test-logs/brute_force_playthrough_latest.txt 2>/dev/null || \
    echo "Warning: No brute_force_playthrough logs found in testbot/test-logs/"
cp testbot/test-logs/brute_force_playthrough_*_summary.txt test-logs/brute_force_playthrough_latest_summary.txt 2>/dev/null
cp testbot/test-logs/brute_force_playthrough_*_report.json test-logs/brute_force_playthrough_latest_report.json 2>/dev/null

echo ""
echo "Check test-logs/ directory for detailed results"
