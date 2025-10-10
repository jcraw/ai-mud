#!/bin/bash

# Test script for AI MUD

set -e  # Exit on error

# Build and install the distribution first
echo "Building distribution..."
gradle installDist --quiet

echo ""
echo "=========================================="
echo "  AI MUD Test - Game is Running!"
echo "=========================================="
echo ""

if grep -q "openai.api.key=" local.properties 2>/dev/null; then
    echo "✅ API key found in local.properties - LLM mode active"
else
    echo "⚠️  No API key in local.properties - using fallback mode"
fi

echo ""
echo "Starting game (will run: look, quit)..."
echo ""

echo -e "look\nquit\ny" | app/build/install/app/bin/app 2>&1 | grep -v "SLF4J"

echo ""
echo "=========================================="
echo "  Test Complete!"
echo "=========================================="
echo ""
echo "To play interactively:"
echo "  app/build/install/app/bin/app"
echo ""
echo "To toggle LLM mode:"
echo "  - Add 'openai.api.key=sk-...' to local.properties (LLM on)"
echo "  - Remove or comment out the key (fallback mode)"
