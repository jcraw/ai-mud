#!/bin/bash
# Test spatial coherence fix for V3 navigation
# This script tests that E→S→W→N navigation returns to the starting position

echo "Testing V3 Spatial Coherence Fix"
echo "================================="
echo ""
echo "This will:"
echo "1. Generate a NEW V3 world with Grid layout"
echo "2. Navigate east, south, west, north"
echo "3. Verify we return to the starting position"
echo ""

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
echo "Running game..."
app/build/install/app/bin/app < /tmp/spatial_test_input.txt > /tmp/spatial_test_output.txt 2>&1

# Extract location IDs from the output
echo ""
echo "Extracting location trail:"
echo "=========================="
grep "Location: SPACE_" /tmp/spatial_test_output.txt | head -6

# Get the first and last locations
START_LOC=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | head -1 | sed 's/.*Location: //')
AFTER_E=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '2p' | sed 's/.*Location: //')
AFTER_S=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '3p' | sed 's/.*Location: //')
AFTER_W=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '4p' | sed 's/.*Location: //')
AFTER_N=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '5p' | sed 's/.*Location: //')
FINAL_LOC=$(grep "Location: SPACE_" /tmp/spatial_test_output.txt | sed -n '6p' | sed 's/.*Location: //')

echo ""
echo "Analysis:"
echo "========="
echo "Start:       $START_LOC"
echo "After E:     $AFTER_E"
echo "After E→S:   $AFTER_S"
echo "After E→S→W: $AFTER_W"
echo "After E→S→W→N: $AFTER_N"
echo "Final (after 'look'): $FINAL_LOC"
echo ""

# Check if we're back at the start
if [ "$START_LOC" = "$FINAL_LOC" ]; then
    echo "✅ SUCCESS: Spatial coherence maintained!"
    echo "   Navigation E→S→W→N returned to starting position."
    exit 0
else
    echo "❌ FAILURE: Spatial coherence broken!"
    echo "   Started at: $START_LOC"
    echo "   Ended at:   $FINAL_LOC"
    echo ""
    echo "Full output saved to: /tmp/spatial_test_output.txt"
    exit 1
fi
