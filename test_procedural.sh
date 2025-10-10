#!/bin/bash
# Test script for procedural dungeon generation

echo "Testing procedural dungeon generation..."
echo ""
echo "This will generate a procedural cave dungeon"
echo ""

# Run the game with input that selects procedural cave (option 4)
# Then uses "look" command and quits
echo -e "4\n5\nlook\ninventory\nhelp\nquit\ny" | app/build/install/app/bin/app
