#!/bin/bash
# Run all three playthrough tests to validate game balance

echo "=========================================="
echo "  Running All Playthrough Tests"
echo "=========================================="
echo ""
echo "This will run 3 comprehensive tests:"
echo "  1. Bad Playthrough (should die)"
echo "  2. Brute Force (should win with gear)"
echo "  3. Smart Playthrough (should win with social skills)"
echo ""
echo "These tests validate:"
echo "  ✓ Game is challenging (bad player dies)"
echo "  ✓ Game is beatable (good player wins)"
echo "  ✓ Multiple solution paths exist"
echo ""

# Create test logs directory
mkdir -p test-logs

echo "=========================================="
echo "Test 1/3: Bad Playthrough"
echo "=========================================="
./test_bad_playthrough.sh

echo ""
echo "=========================================="
echo "Test 2/3: Brute Force Playthrough"
echo "=========================================="
./test_brute_force_playthrough.sh

echo ""
echo "=========================================="
echo "Test 3/3: Smart Playthrough"
echo "=========================================="
./test_smart_playthrough.sh

echo ""
echo "=========================================="
echo "  All Tests Complete!"
echo "=========================================="
echo ""
echo "Check test-logs/ directory for detailed results"
echo "  - bad_playthrough_*"
echo "  - brute_force_playthrough_*"
echo "  - smart_playthrough_*"
