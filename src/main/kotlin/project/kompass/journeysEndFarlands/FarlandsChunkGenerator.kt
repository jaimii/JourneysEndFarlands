package project.kompass.journeysEndFarlands

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random
import kotlin.math.absoluteValue

class FarlandsChunkGenerator : ChunkGenerator() {

    // Generate Farlands 128 blocks before the world border.
    private data class Boundaries(val minX: Double, val maxX: Double, val minZ: Double, val maxZ: Double)

    private fun getFarlandsBoundaries(worldInfo: WorldInfo): Boundaries {
        val world = Bukkit.getWorld(worldInfo.uid) ?: Bukkit.getWorld(worldInfo.name)

        var minX = -30_000_000.0 + 128.0
        var maxX = 30_000_000.0 - 128.0
        var minZ = -30_000_000.0 + 128.0
        var maxZ = 30_000_000.0 - 128.0

        if (world != null) {
            val border = world.worldBorder
            val size = border.size
            val center = border.center
            val half = size / 2.0
            val offset = 128.0

            if (size > 256.0) {
                minX = center.x - half + offset
                maxX = center.x + half - offset
                minZ = center.z - half + offset
                maxZ = center.z + half - offset
            } else {
                minX = center.x - 16.0
                maxX = center.x + 16.0
                minZ = center.z - 16.0
                maxZ = center.z + 16.0
            }
        }
        return Boundaries(minX, maxX, minZ, maxZ)
    }

    private fun isFarlandsChunk(worldInfo: WorldInfo, chunkX: Int, chunkZ: Int): Boolean {
        val boundaries = getFarlandsBoundaries(worldInfo)
        val chunkMinX = chunkX * 16
        val chunkMaxX = chunkMinX + 15
        val chunkMinZ = chunkZ * 16
        val chunkMaxZ = chunkMinZ + 15

        return chunkMinX < boundaries.minX || chunkMaxX > boundaries.maxX ||
                chunkMinZ < boundaries.minZ || chunkMaxZ > boundaries.maxZ
    }

    override fun shouldGenerateNoise(): Boolean = true
    override fun shouldGenerateSurface(): Boolean = true
    override fun shouldGenerateMobs(): Boolean = true

    override fun shouldGenerateCaves(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int): Boolean {
        return !isFarlandsChunk(worldInfo, chunkX, chunkZ)
    }

    override fun shouldGenerateDecorations(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int): Boolean {
        return !isFarlandsChunk(worldInfo, chunkX, chunkZ)
    }

    override fun shouldGenerateStructures(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int): Boolean {
        return !isFarlandsChunk(worldInfo, chunkX, chunkZ)
    }

    private fun getCoordRandom(x: Long, y: Long, z: Long, seed: Long): Float {
        var h = seed xor (x * 341873128712L) xor (y * 132897987541L) xor (z * 343891823719L)
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 33)
        val floatVal = (h and 0xFFFFFFF).toFloat() / 0xFFFFFFF.toFloat()
        return floatVal.coerceIn(0.0f, 1.0f)
    }

    override fun generateSurface(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        chunkData: ChunkData
    ) {
        val boundaries = getFarlandsBoundaries(worldInfo)
        val chunkMinX = chunkX * 16
        val chunkMinZ = chunkZ * 16

        val isFarlandsRegion = isFarlandsChunk(worldInfo, chunkX, chunkZ)

        if (isFarlandsRegion) {
            val lowNoiseGen = FastNoise(worldInfo.seed)
            val highNoiseGen = FastNoise(worldInfo.seed + 1)
            val selectorNoiseGen = FastNoise(worldInfo.seed + 2)

            // Single-octave detail noises for block variation
            val stoneNoiseGen = FastNoise(worldInfo.seed + 3)
            val lushNoiseGen = FastNoise(worldInfo.seed + 4)

            val minY = chunkData.minHeight
            val maxY = chunkData.maxHeight
            val ySamples = (maxY - minY) / 8 + 1

            // Coarse 3D density grid (sampled horizontally every 4 blocks, vertically every 8)
            val grid = Array(5) { Array(ySamples) { DoubleArray(5) } }

            for (gX in 0..4) {
                val localX = gX * 4
                val absX = (chunkMinX + localX).toDouble()

                val distX = if (absX < boundaries.minX) boundaries.minX - absX else if (absX > boundaries.maxX) absX - boundaries.maxX else 0.0
                val noiseX = if (distX > 0.0) {
                    val sign = if (absX < 0) -1.0 else 1.0
                    sign * (12550821.0 + distX)
                } else {
                    absX
                }

                for (gZ in 0..4) {
                    val localZ = gZ * 4
                    val absZ = (chunkMinZ + localZ).toDouble()

                    val distZ = if (absZ < boundaries.minZ) boundaries.minZ - absZ else if (absZ > boundaries.maxZ) absZ - boundaries.maxZ else 0.0
                    val noiseZ = if (distZ > 0.0) {
                        val sign = if (absZ < 0) -1.0 else 1.0
                        sign * (12550821.0 + distZ)
                    } else {
                        absZ
                    }

                    for (gY in 0 until ySamples) {
                        val absY = minY + gY * 8
                        grid[gX][gY][gZ] = calculateDensity(lowNoiseGen, highNoiseGen, selectorNoiseGen, noiseX, absY.toDouble(), noiseZ)
                    }
                }
            }

            for (x in 0..15) {
                val x0 = x / 4
                val x1 = x0 + 1
                val tx = (x % 4) / 4.0

                val absX = (chunkMinX + x).toDouble()
                val absXLong = absX.toLong()

                for (z in 0..15) {
                    val z0 = z / 4
                    val z1 = z0 + 1
                    val tz = (z % 4) / 4.0

                    val absZ = (chunkMinZ + z).toDouble()
                    val absZLong = absZ.toLong()

                    var solidCount = 0
                    var wasAboveSolid = false

                    for (y in (maxY - 1) downTo minY) {
                        if (y == minY) {
                            chunkData.setBlock(x, y, z, Material.BEDROCK)
                            continue
                        }

                        val yLong = y.toLong()

                        // Vertical sampling boundaries
                        val y0 = (y - minY) / 8
                        val y1 = y0 + 1
                        val ty = ((y - minY) % 8) / 8.0

                        // Retrieve density endpoints
                        val d000 = grid[x0][y0][z0]
                        val d001 = grid[x0][y0][z1]
                        val d010 = grid[x0][y1][z0]
                        val d011 = grid[x0][y1][z1]
                        val d100 = grid[x1][y0][z0]
                        val d101 = grid[x1][y0][z1]
                        val d110 = grid[x1][y1][z0]
                        val d111 = grid[x1][y1][z1]

                        // Trilinear interpolation formula
                        val d00 = d000 + tx * (d100 - d000)
                        val d01 = d001 + tx * (d101 - d001)
                        val d10 = d010 + tx * (d110 - d010)
                        val d11 = d011 + tx * (d111 - d011)

                        val d0 = d00 + ty * (d10 - d00)
                        val d1 = d01 + ty * (d11 - d01)

                        val density = d0 + tz * (d1 - d0)

                        if (density > 0.0) {
                            solidCount++

                            val isDeepslate = when {
                                y >= 8 -> false
                                y <= -8 -> true
                                else -> {
                                    val progress = (8.0 - y) / 16.0
                                    getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 12345L) < progress
                                }
                            }

                            val stoneNoise = stoneNoiseGen.noise(absX * 0.04, y * 0.04, absZ * 0.04)
                            val baseSolidBlock = if (isDeepslate) {
                                if (stoneNoise > 0.35) Material.TUFF else Material.DEEPSLATE
                            } else {
                                when {
                                    stoneNoise > 0.30 -> Material.ANDESITE
                                    stoneNoise < -0.30 -> Material.DIORITE
                                    stoneNoise in -0.1..0.1 -> Material.GRANITE
                                    else -> Material.STONE
                                }
                            }

                            val lushNoise = lushNoiseGen.noise(absX * 0.012, y * 0.012, absZ * 0.012)
                            val isLush = lushNoise > 0.35 && y < 80

                            val isOreVein = getCoordRandom(absXLong / 4L, yLong / 4L, absZLong / 4L, worldInfo.seed + 8888L) < 0.08
                            val oreRoll = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 9999L)

                            val material = when {
                                isLush && solidCount == 1 -> {
                                    val roll = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 11111L)
                                    if (roll < 0.65) Material.MOSS_BLOCK else if (roll < 0.85) Material.CLAY else Material.GRASS_BLOCK
                                }
                                solidCount == 1 -> Material.GRASS_BLOCK
                                solidCount in 2..4 -> {
                                    val mossRoll = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 22222L)
                                    if (isLush && mossRoll < 0.50) Material.MOSS_BLOCK else Material.DIRT
                                }
                                isOreVein -> {
                                    val oreType = chooseOreType(y.toDouble(), oreRoll, isDeepslate)
                                    oreType ?: baseSolidBlock
                                }
                                else -> baseSolidBlock
                            }

                            chunkData.setBlock(x, y, z, material)

                            if (solidCount == 1 && y < maxY - 1) {
                                val roll = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 77777L)
                                if (isLush) {
                                    when {
                                        roll < 0.15 -> chunkData.setBlock(x, y + 1, z, Material.MOSS_CARPET)
                                        roll < 0.25 -> chunkData.setBlock(x, y + 1, z, Material.AZALEA)
                                        roll < 0.30 -> chunkData.setBlock(x, y + 1, z, Material.FLOWERING_AZALEA)
                                        roll < 0.35 -> chunkData.setBlock(x, y + 1, z, Material.BIG_DRIPLEAF)
                                    }
                                } else {
                                    when {
                                        roll < 0.008 -> {
                                            val treeTypeRoll = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 66666L)
                                            val treeSeed = absXLong * 3121L xor yLong * 2113L xor absZLong * 7213L xor worldInfo.seed
                                            val treeRand = Random(treeSeed)
                                            when {
                                                treeTypeRoll < 0.45 -> generateTree(chunkData, x, y + 1, z, treeRand, Material.OAK_LOG, Material.OAK_LEAVES)
                                                treeTypeRoll < 0.75 -> generateTree(chunkData, x, y + 1, z, treeRand, Material.BIRCH_LOG, Material.BIRCH_LEAVES)
                                                else -> generateSpruceTree(chunkData, x, y + 1, z, treeRand)
                                            }
                                        }
                                        roll < 0.20 -> chunkData.setBlock(x, y + 1, z, Material.SHORT_GRASS)
                                        roll < 0.23 -> chunkData.setBlock(x, y + 1, z, Material.FERN)
                                        roll < 0.25 -> {
                                            val flower = if (getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 55555L) < 0.5) Material.DANDELION else Material.POPPY
                                            chunkData.setBlock(x, y + 1, z, flower)
                                        }
                                    }
                                }
                            }
                            wasAboveSolid = true
                        } else {
                            solidCount = 0
                            chunkData.setBlock(x, y, z, Material.AIR)

                            if (wasAboveSolid && y < maxY - 1) {
                                val randomVal = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 44444L)
                                if (randomVal < 0.0012) {
                                    val springMaterial = if (y < -10) Material.LAVA else Material.WATER
                                    chunkData.setBlock(x, y, z, springMaterial)
                                }
                            }

                            val lushNoise = lushNoiseGen.noise(absX * 0.012, y * 0.012, absZ * 0.012)
                            val isLush = lushNoise > 0.35 && y < 80
                            if (isLush && wasAboveSolid && y < maxY - 1) {
                                val ceilingRoll = getCoordRandom(absXLong, yLong, absZLong, worldInfo.seed + 33333L)
                                when {
                                    ceilingRoll < 0.08 -> chunkData.setBlock(x, y, z, Material.SPORE_BLOSSOM)
                                    ceilingRoll < 0.25 -> chunkData.setBlock(x, y, z, Material.CAVE_VINES)
                                }
                            }
                            wasAboveSolid = false
                        }
                    }
                }
            }
        }
    }

    private fun calculateDensity(
        lowNoiseGen: FastNoise,
        highNoiseGen: FastNoise,
        selectorNoiseGen: FastNoise,
        noiseX: Double,
        noiseY: Double,
        noiseZ: Double
    ): Double {
        val scaleXZ = 684.412 / 80.0
        val scaleY = 684.412 / 160.0

        // Selector noise has 8 octaves
        val selectorValue = (selectorNoiseGen.fractalNoise(noiseX, noiseY, noiseZ, scaleXZ / 80.0, scaleY / 160.0, 8) / 10.0 + 1.0) / 2.0

        // lowNoise has 16 octaves
        val lowValue = lowNoiseGen.fractalNoise(noiseX, noiseY, noiseZ, scaleXZ, scaleY, 16) / 512.0

        // highNoise has 16 octaves
        val highValue = highNoiseGen.fractalNoise(noiseX, noiseY, noiseZ, scaleXZ, scaleY, 16) / 512.0

        val baseNoise = when {
            selectorValue < 0.0 -> lowValue
            selectorValue > 1.0 -> highValue
            else -> lowValue + (highValue - lowValue) * selectorValue
        }

        val amplified = baseNoise * 15.0

        val targetHeight = 64.0
        val heightScale = 48.0
        val heightGradient = (noiseY - targetHeight) / heightScale

        return amplified - heightGradient
    }

    private fun generateTree(chunkData: ChunkData, x: Int, startY: Int, z: Int, random: Random, logType: Material, leafType: Material) {
        val treeHeight = 4 + random.nextInt(3)

        if (startY + treeHeight + 2 >= chunkData.maxHeight) return

        for (dy in 0 until treeHeight) {
            val ty = startY + dy
            if (ty in chunkData.minHeight until chunkData.maxHeight) {
                chunkData.setBlock(x, ty, z, logType)
            }
        }

        val leafStartY = startY + treeHeight - 3
        val leafEndY = startY + treeHeight

        for (ty in leafStartY..leafEndY) {
            val isTop = ty == leafEndY
            val radius = if (isTop) 1 else 2

            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius == 2 && dx.absoluteValue == 2 && dz.absoluteValue == 2) {
                        if (random.nextFloat() < 0.5) continue
                    }

                    val tx = x + dx
                    val tz = z + dz

                    if (tx in 0..15 && tz in 0..15) {
                        if (chunkData.getType(tx, ty, tz) == Material.AIR) {
                            chunkData.setBlock(tx, ty, tz, leafType)
                        }
                    }
                }
            }
        }

        val topY = leafEndY + 1
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx.absoluteValue == 1 && dz.absoluteValue == 1) continue
                val tx = x + dx
                val tz = z + dz
                if (tx in 0..15 && tz in 0..15) {
                    if (chunkData.getType(tx, topY, tz) == Material.AIR) {
                        chunkData.setBlock(tx, topY, tz, leafType)
                    }
                }
            }
        }
    }

    private fun generateSpruceTree(chunkData: ChunkData, x: Int, startY: Int, z: Int, random: Random) {
        val treeHeight = 5 + random.nextInt(3)

        if (startY + treeHeight + 2 >= chunkData.maxHeight) return

        for (dy in 0 until treeHeight) {
            val ty = startY + dy
            if (ty in chunkData.minHeight until chunkData.maxHeight) {
                chunkData.setBlock(x, ty, z, Material.SPRUCE_LOG)
            }
        }

        val leafStartY = startY + 2
        val leafEndY = startY + treeHeight

        for (ty in leafStartY..leafEndY) {
            val distFromTop = leafEndY - ty
            val radius = when {
                distFromTop == 0 -> 0
                distFromTop == 1 -> 1
                distFromTop % 2 == 0 -> 2
                else -> 1
            }

            if (radius > 0) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (radius == 2 && dx.absoluteValue == 2 && dz.absoluteValue == 2) {
                            if (random.nextFloat() < 0.5) continue
                        }

                        val tx = x + dx
                        val tz = z + dz

                        if (tx in 0..15 && tz in 0..15) {
                            if (ty in chunkData.minHeight until chunkData.maxHeight) {
                                if (chunkData.getType(tx, ty, tz) == Material.AIR) {
                                    chunkData.setBlock(tx, ty, tz, Material.SPRUCE_LEAVES)
                                }
                            }
                        }
                    }
                }
            }
        }

        val topY = leafEndY + 1
        if (topY in chunkData.minHeight until chunkData.maxHeight && x in 0..15 && z in 0..15) {
            if (chunkData.getType(x, topY, z) == Material.AIR) {
                chunkData.setBlock(x, topY, z, Material.SPRUCE_LEAVES)
            }
        }
    }

    private fun chooseOreType(y: Double, roll: Float, isDeepslate: Boolean): Material? {
        return if (isDeepslate) {
            when {
                y in -64.0..16.0 && roll < 0.05 -> Material.DEEPSLATE_DIAMOND_ORE
                y in -64.0..15.0 && roll < 0.12 -> Material.DEEPSLATE_REDSTONE_ORE
                y in -64.0..64.0 && roll < 0.15 -> Material.DEEPSLATE_LAPIS_ORE
                y in -64.0..32.0 && roll < 0.18 -> Material.DEEPSLATE_GOLD_ORE
                y in -16.0..112.0 && roll < 0.25 -> Material.DEEPSLATE_IRON_ORE
                y in -16.0..112.0 && roll < 0.30 -> Material.DEEPSLATE_COPPER_ORE
                y in -64.0..0.0 && roll < 0.02 -> Material.DEEPSLATE_EMERALD_ORE
                else -> null
            }
        } else {
            when {
                y in 96.0..320.0 && roll < 0.20 -> Material.COAL_ORE
                y in 0.0..192.0 && roll < 0.30 -> Material.COAL_ORE
                y in 0.0..320.0 && roll < 0.38 -> Material.IRON_ORE
                y in 0.0..112.0 && roll < 0.45 -> Material.COPPER_ORE
                y in 0.0..32.0 && roll < 0.48 -> Material.GOLD_ORE
                y in 0.0..64.0 && roll < 0.51 -> Material.LAPIS_ORE
                y in -16.0..16.0 && roll < 0.53 -> Material.DIAMOND_ORE
                y > 100.0 && roll > 0.97 -> Material.EMERALD_ORE
                else -> null
            }
        }
    }
}