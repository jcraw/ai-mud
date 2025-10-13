#!/bin/bash
# Test the Bad Playthrough scenario - player should die to boss without gear

echo "Running Bad Playthrough Test..."
echo "Expected: Player rushes to throne room and dies to Skeleton King"
echo ""

# Run test with scenario 9 (Bad Playthrough)
echo "1" | gradle :testbot:run --console=plain 2>&1 | tee test-logs/bad_playthrough_latest.log << EOF
1
9
EOF

echo ""
echo "Check test-logs/ directory for detailed results"
