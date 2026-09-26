package me.dvyy.particles.compute.simulation

import de.fabmax.kool.modules.ksl.KslComputeShader
import de.fabmax.kool.modules.ksl.lang.*
import me.dvyy.particles.compute.partitioning.WORK_GROUP_SIZE

private const val WALL_CONTACT_DISTANCE = 5.512f

class VerletHalfStepShader {
    val shader = KslComputeShader("Fields Half-Step") {
        computeStage(WORK_GROUP_SIZE) {
            val dT = uniformFloat1("dT")
            val positions = storage<KslFloat4>("positions")
            val velocities = storage<KslFloat4>("velocities")
            val forces = storage<KslFloat4>("forces")
            val boxMax = uniformFloat3("boxMax")
            val wallCollisionCount = storage<KslInt1>("wallCollisionCount")
            val wallContactStates = storage<KslInt1>("wallContactStates")
            val countWallCollisions = uniformInt1("countWallCollisions")

            main {
                val id = int1Var(inGlobalInvocationId.x.toInt1())
                val position = float3Var(positions[id].xyz) //p(t)
                val velocity = float3Var(velocities[id].xyz) //v(t)
                val force = float3Var(forces[id].xyz) //a(t)

                // Verlet integration with half-step eliminated p(t + dt) = p(t) + v(t)dT + 1/2 a(t)dT^2
                val nextPosition = float3Var(position + (velocity * dT) + ((force * dT * dT) / 2f.const))

                val previousContacts = int1Var(wallContactStates[id])
                val currentContacts = int1Var(0.const)
                val collisionCount = int1Var(0.const)
                `if`(nextPosition.x lt WALL_CONTACT_DISTANCE.const) {
                    currentContacts += 1.const
                    `if`((previousContacts and 1.const) eq 0.const) {
                        `if`(velocity.x lt 0f.const) { collisionCount += 1.const }
                    }
                }
                `if`((boxMax.x - nextPosition.x) lt WALL_CONTACT_DISTANCE.const) {
                    currentContacts += 2.const
                    `if`((previousContacts and 2.const) eq 0.const) {
                        `if`(velocity.x gt 0f.const) { collisionCount += 1.const }
                    }
                }
                `if`(nextPosition.y lt WALL_CONTACT_DISTANCE.const) {
                    currentContacts += 4.const
                    `if`((previousContacts and 4.const) eq 0.const) {
                        `if`(velocity.y lt 0f.const) { collisionCount += 1.const }
                    }
                }
                `if`((boxMax.y - nextPosition.y) lt WALL_CONTACT_DISTANCE.const) {
                    currentContacts += 8.const
                    `if`((previousContacts and 8.const) eq 0.const) {
                        `if`(velocity.y gt 0f.const) { collisionCount += 1.const }
                    }
                }
                `if`(boxMax.z gt 0f.const) {
                    `if`(nextPosition.z lt WALL_CONTACT_DISTANCE.const) {
                        currentContacts += 16.const
                        `if`((previousContacts and 16.const) eq 0.const) {
                            `if`(velocity.z lt 0f.const) { collisionCount += 1.const }
                        }
                    }
                    `if`((boxMax.z - nextPosition.z) lt WALL_CONTACT_DISTANCE.const) {
                        currentContacts += 32.const
                        `if`((previousContacts and 32.const) eq 0.const) {
                            `if`(velocity.z gt 0f.const) { collisionCount += 1.const }
                        }
                    }
                }
                wallContactStates[id] = currentContacts
                `if`(countWallCollisions eq 1.const) {
                    int1Var(wallCollisionCount.atomicAdd(0.const, collisionCount))
                }

                // Ensure particles are in bounds (periodic boundary)
                nextPosition set clamp(
                    nextPosition,// - (boxMax * floor(nextPosition / boxMax)), // modulo box size
                    0f.const3, boxMax, // clamp in box boundary for the case where boxMax has a dimension with size 0
                )
//                nextPosition set clamp(nextPosition, 0f.const3, boxMax)
                positions[id] = float4Value(nextPosition, 0f.const)
            }
        }
    }

    var dT by shader.uniform1f("dT")
    var positions by shader.storage("positions")
    var velocities by shader.storage("velocities")
    var forces by shader.storage("forces")
    var boxMax by shader.uniform3f("boxMax")
    var wallCollisionCount by shader.storage("wallCollisionCount")
    var wallContactStates by shader.storage("wallContactStates")
    var countWallCollisionsEnabled by shader.uniform1i("countWallCollisions")
}
