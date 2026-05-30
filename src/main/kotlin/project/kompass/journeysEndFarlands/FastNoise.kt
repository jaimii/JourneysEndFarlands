package project.kompass.journeysEndFarlands

import java.util.Random

class FastNoise(seed: Long) {
    private val p = IntArray(512)

    init {
        val rand = Random(seed)
        for (i in 0..255) {
            p[i] = i
        }
        for (i in 0..255) {
            val j = rand.nextInt(256)
            val temp = p[i]
            p[i] = p[j]
            p[j] = temp
        }
        for (i in 0..255) {
            p[256 + i] = p[i]
        }
    }

    private fun fade(t: Double): Double {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }

    private fun lerp(t: Double, a: Double, b: Double): Double {
        return a + t * (b - a)
    }

    private fun grad(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }

    fun noise(x: Double, y: Double, z: Double): Double {
        // Direct reconstruction of Java's 3D Perlin Noise
        var i = x.toInt()
        var j = y.toInt()
        var k = z.toInt()

        if (x < i.toDouble()) i--
        if (y < j.toDouble()) j--
        if (z < k.toDouble()) k--

        val X = i and 255
        val Y = j and 255
        val Z = k and 255

        // Fractional coordinates grow outward after coordinates exceed signed integer limits
        val xf = x - i.toDouble()
        val yf = y - j.toDouble()
        val zf = z - k.toDouble()

        // Uncoerced fade function
        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)

        val A = p[X] + Y
        val AA = p[A] + Z
        val AB = p[A + 1] + Z
        val B = p[X + 1] + Y
        val BA = p[B] + Z
        val BB = p[B + 1] + Z

        val gradAA = grad(p[AA], xf, yf, zf)
        val gradBA = grad(p[BA], xf - 1.0, yf, zf)
        val gradAB = grad(p[AB], xf, yf - 1.0, zf)
        val gradBB = grad(p[BB], xf - 1.0, yf - 1.0, zf)

        val gradAA1 = grad(p[AA + 1], xf, yf, zf - 1.0)
        val gradBA1 = grad(p[BA + 1], xf - 1.0, yf, zf - 1.0)
        val gradAB1 = grad(p[AB + 1], xf, yf - 1.0, zf - 1.0)
        val gradBB1 = grad(p[BB + 1], xf - 1.0, yf - 1.0, zf - 1.0)

        val result = lerp(
            w,
            lerp(
                v,
                lerp(u, gradAA, gradBA),
                lerp(u, gradAB, gradBB)
            ),
            lerp(
                v,
                lerp(u, gradAA1, gradBA1),
                lerp(u, gradAB1, gradBB1)
            )
        )

        return if (result.isNaN() || result.isInfinite()) 0.0 else result
    }

    // Accumulates fractal noise sum matching standard Minecraft Beta octaves
    fun fractalNoise(
        x: Double,
        y: Double,
        z: Double,
        scaleXZ: Double,
        scaleY: Double,
        octaves: Int
    ): Double {
        var value = 0.0
        var amplitude = 1.0
        var frequency = 1.0

        for (i in 0 until octaves) {
            val nx = x * scaleXZ * frequency
            val ny = y * scaleY * frequency
            val nz = z * scaleXZ * frequency

            value += noise(nx, ny, nz) * amplitude
            amplitude *= 0.5
            frequency *= 2.0
        }
        return value
    }
}