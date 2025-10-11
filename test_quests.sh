#!/bin/bash
# Test script for quest testing scenario
# This script runs the test bot with the quest testing scenario

set -e  # Exit on error

echo "=========================================="
echo "  AI MUD - Quest Testing Scenario"
echo "=========================================="
echo ""

if grep -q "openai.api.key=" local.properties 2>/dev/null || [ -n "$OPENAI_API_KEY" ]; then
    echo "✅ API key found - test bot will run"
else
    echo "❌ No API key found!"
    echo "   Set OPENAI_API_KEY environment variable or add to local.properties"
    exit 1
fi

echo ""
echo "Building project..."
gradle installDist --quiet

echo ""
echo "Running quest testing scenario..."
echo "This will automatically select:"
echo "  - Dungeon: Sample Dungeon (option 1)"
echo "  - Scenario: Quest Testing (option 6)"
echo ""

# Use echo to provide input: 1 for sample dungeon, 6 for quest testing
echo -e "1\n6" | gradle :testbot:run --quiet --console=plain 2>&1 | grep -v "SLF4J"

echo ""
echo "=========================================="
echo "  Test Complete!"
echo "=========================================="
echo ""
echo "Check test-logs/ directory for detailed results"
echo ""
