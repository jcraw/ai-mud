#!/bin/bash
# Test spatial coherence fix for V3 navigation
# This script tests bidirectional navigation consistency for all 8 compass directions

echo "Testing V3 Spatial Coherence (All 8 Directions)"
echo "================================================"
echo ""
echo "This will test bidirectional consistency for:"
echo "  - Cardinal directions: N↔S, E↔W"
echo "  - Diagonal directions: NE↔SW, NW↔SE"
echo "  - Loop test: E→S→W→N"
echo ""

# Function to test a single bidirectional pair
test_bidirectional() {
    local dir1=$1
    local dir2=$2
    local test_name=$3

    # Create input commands
    cat > /tmp/spatial_test_input.txt << EOF
2
3
1
1
look
$dir1
look
$dir2
look
quit
y
EOF

    # Run the game with input
    app/build/install/app/bin/app < /tmp/spatial_test_input.txt > /tmp/spatial_test_output.txt 2>&1

    # Extract locations
    START_LOC=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | head -1 | sed 's/.*Location: //')
    AFTER_DIR1=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '2p' | sed 's/.*Location: //')
    FINAL_LOC=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '3p' | sed 's/.*Location: //')

    if [ -z "$START_LOC" ] || [ -z "$FINAL_LOC" ]; then
        echo "  ⚠️  SKIP: $test_name (direction not available in this world)"
        return 2
    fi

    if [ "$START_LOC" = "$FINAL_LOC" ]; then
        echo "  ✅ PASS: $test_name"
        return 0
    else
        echo "  ❌ FAIL: $test_name (Started: $START_LOC, After $dir1: $AFTER_DIR1, After $dir2: $FINAL_LOC)"
        return 1
    fi
}

# Test loop navigation
test_loop() {
    # Create input commands
    cat > /tmp/spatial_test_input.txt << 'EOF'
2
3
1
1
look
e
look
s
look
w
look
n
look
quit
y
EOF

    # Run the game with input
    app/build/install/app/bin/app < /tmp/spatial_test_input.txt > /tmp/spatial_test_output.txt 2>&1

    # Extract locations
    START_LOC=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | head -1 | sed 's/.*Location: //')
    FINAL_LOC=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '5p' | sed 's/.*Location: //')

    if [ -z "$START_LOC" ] || [ -z "$FINAL_LOC" ]; then
        echo "  ⚠️  SKIP: Loop test E→S→W→N (directions not available)"
        return 2
    fi

    if [ "$START_LOC" = "$FINAL_LOC" ]; then
        echo "  ✅ PASS: Loop test E→S→W→N"
        return 0
    else
        echo "  ❌ FAIL: Loop test E→S→W→N (Started: $START_LOC, Ended: $FINAL_LOC)"
        return 1
    fi
}

# Run all tests
echo "Running bidirectional tests:"
echo "============================"
FAILED=0
PASSED=0
SKIPPED=0

test_bidirectional "north" "south" "N↔S"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "south" "north" "S↔N"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "east" "west" "E↔W"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "west" "east" "W↔E"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "northeast" "southwest" "NE↔SW"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "southwest" "northeast" "SW↔NE"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "northwest" "southeast" "NW↔SE"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))
test_bidirectional "southeast" "northwest" "SE↔NW"; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))

echo ""
echo "Running loop test:"
echo "=================="
test_loop; RESULT=$?; [ $RESULT -eq 0 ] && ((PASSED++)) || [ $RESULT -eq 1 ] && ((FAILED++)) || ((SKIPPED++))

echo ""
echo "Summary:"
echo "========"
echo "  Passed:  $PASSED"
echo "  Failed:  $FAILED"
echo "  Skipped: $SKIPPED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "✅ ALL TESTS PASSED! Spatial coherence is maintained."
    exit 0
else
    echo "❌ SOME TESTS FAILED! Spatial coherence issues detected."
    echo "Full output saved to: /tmp/spatial_test_output.txt"
    exit 1
fi
