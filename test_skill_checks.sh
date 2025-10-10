#!/bin/bash
# Test script for skill check system

echo "Building game..."
gradle installDist

echo ""
echo "Starting skill check test sequence..."
echo ""
echo "Commands to test:"
echo "1. n (move to corridor)"
echo "2. look (should see Suspicious Loose Stone)"
echo "3. check stone (WIS check, DC 10 - should succeed)"
echo "4. e (move to treasury)"
echo "5. look (should see Locked Ornate Chest)"
echo "6. check chest (DEX check, DC 15 - might fail)"
echo "7. n, n, n (move to secret chamber)"
echo "8. look (should see Heavy Stone Door and Ancient Rune Inscription)"
echo "9. check door (STR check, DC 20 - hard)"
echo "10. check rune (INT check, DC 15)"
echo "11. quit"
echo ""
echo "Press Enter to continue..."
read

# Run the game with test commands
echo -e "n\nlook\ncheck stone\ne\nlook\ncheck chest\nw\nn\nn\nlook\ncheck door\ncheck rune\nquit\ny" | app/build/install/app/bin/app
