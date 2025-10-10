#!/bin/bash

# Test script for combat mechanics
# Navigates to the Throne Room and attacks the Skeleton King

echo "Testing combat mechanics..."
echo ""
echo "Commands:"
echo "1. north (to Dark Corridor)"
echo "2. north (to Throne Room)"
echo "3. look"
echo "4. attack skeleton (initiate combat)"
echo "5. attack (continue attacking until victory or death)"
echo ""
echo "Sending commands..."
echo ""

app/build/install/app/bin/app <<EOF
north
north
look
attack skeleton
attack
attack
attack
attack
attack
attack
attack
quit
y
EOF
