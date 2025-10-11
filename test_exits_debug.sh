#!/bin/bash

# Temporary test to debug room exit generation

cat > /tmp/TestExits.kt << 'EOF'
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.reasoning.procedural.DungeonTheme

fun main() {
    val worldState = ProceduralDungeonBuilder.generateCrypt(10)

    println("\n=== Room Exit Analysis ===")
    worldState.rooms.forEach { (id, room) ->
        println("\n${room.name} ($id):")
        println("  Exits (${room.exits.size}): ${room.exits.keys.joinToString(", ") { it.displayName }}")
        println("  Exit map: ${room.exits}")
    }
}
EOF

# Compile and run
cd reasoning
kotlinc -cp "../core/build/libs/core.jar:../utils/build/libs/utils.jar" -script /tmp/TestExits.kt
