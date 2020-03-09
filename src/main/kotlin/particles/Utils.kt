package particles

import org.openrndr.draw.ColorBuffer
import org.openrndr.math.Vector4

fun fillBuffer(buffer: ColorBuffer, iterate: (x: Int, y: Int) -> Vector4) {
    val shadow = buffer.shadow

    for (y in 0 until buffer.effectiveWidth) {
        for (x in 0 until buffer.effectiveHeight) {
            val vector = iterate(x, y)

            shadow.write(x, y, vector.x, vector.y, vector.z, vector.w)
        }
    }

    shadow.upload()
}

fun fillBuffer3D(buffer: ColorBuffer, depth: Int, iterate: (x: Int, y: Int, z: Int) -> Vector4) {
    val shadow = buffer.shadow

    for (y in 0 until buffer.effectiveWidth) {
        for (x in 0 until buffer.effectiveHeight) {
            for (z in 0 until buffer.effectiveHeight) {
                val vector = iterate(x, y, z)

                shadow.write(x, y, vector.x, vector.y, vector.z, vector.w)
            }
        }
    }

    shadow.upload()
}