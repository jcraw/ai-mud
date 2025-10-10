#!/bin/bash
# Test save/load functionality

echo "Testing Save/Load Functionality"
echo "================================"
echo ""
echo "This test will:"
echo "1. Start the game in the sample dungeon"
echo "2. Move north, pick up sword"
echo "3. Save the game"
echo "4. Move around more"
echo "5. Load the saved game"
echo "6. Verify we're back to the saved location"
echo ""
echo "Commands to execute:"
echo "  1           (select sample dungeon)"
echo "  n           (move north)"
echo "  take sword  (pick up iron sword)"
echo "  inventory   (check inventory)"
echo "  save test1  (save game)"
echo "  s           (move south)"
echo "  load test1  (load saved game)"
echo "  inventory   (verify sword is still there)"
echo "  quit        (exit)"
echo ""
read -p "Press Enter to start the test..."

gradle installDist && echo -e "1\nn\ntake sword\ninventory\nsave test1\ns\nload test1\ninventory\nquit\ny" | app/build/install/app/bin/app
