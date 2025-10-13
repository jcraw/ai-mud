#!/bin/bash
# Test the Brute Force Playthrough scenario - player collects gear and defeats boss

echo "Running Brute Force Playthrough Test..."
echo "Expected: Player collects best gear (Iron Sword +5, Chainmail +4) and defeats Skeleton King"
echo ""

# Run test with scenario 10 (Brute Force Playthrough)
echo "1" | gradle :testbot:run --console=plain 2>&1 | tee test-logs/brute_force_playthrough_latest.log << EOF
1
10
EOF

echo ""
echo "Check test-logs/ directory for detailed results"
