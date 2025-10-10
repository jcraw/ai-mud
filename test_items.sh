#!/bin/bash

# Test script for item usage system
# Tests weapon equipping, consumables, and combat with weapons

echo "Testing item usage system..."
echo ""
echo "Test Plan:"
echo "1. Go to armory and pick up iron sword"
echo "2. Check inventory"
echo "3. Equip the sword"
echo "4. Go to treasury and get health potion"
echo "5. Check inventory again"
echo "6. Go to throne room and attack Skeleton King"
echo "7. Use health potion during combat"
echo "8. Continue attacking until victory"
echo ""
echo "Running test..."
echo ""

app/build/install/app/bin/app <<EOF
north
west
take iron sword
inventory
equip iron sword
inventory
east
east
take health potion
inventory
west
north
attack skeleton
attack
attack
use potion
attack
attack
attack
attack
attack
quit
y
EOF
