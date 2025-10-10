#!/bin/bash

# Test script for AI MUD

echo "Testing game without LLM (fallback mode)..."
echo -e "look\nquit\ny" | app/build/install/app/bin/app

echo ""
echo "=========================================="
echo ""
echo "To test with LLM descriptions, set OPENAI_API_KEY and run:"
echo "export OPENAI_API_KEY='your-key-here'"
echo "app/build/install/app/bin/app"
