import particles.ParticlesManager
import org.openrndr.application
import org.openrndr.draw.*
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.palette.PaletteStudio
import org.openrndr.extras.camera.OrbitalCamera
import org.openrndr.extras.camera.OrbitalControls
import org.openrndr.math.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val paletteStudio = PaletteStudio( sortBy = PaletteStudio.SortBy.BRIGHTEST)

fun main() = application {
    configure {
        width = 1080
        height = 1080
    }

    program {
        paletteStudio.select(92)

//        val geometry = vertexBuffer(vertexFormat {
//            position(3)
//        }, 1)
//
//        geometry.put {
//            write(Vector3.ONE)
//        }

        // -- create the vertex buffer
        val geometry = vertexBuffer(vertexFormat {
            position(3)
            textureCoordinate(2)
        }, 4)

        geometry.put {
            write(Vector3(-1.0, -1.0, 0.0))
            write(Vector2(0.0, 1.0))

            write(Vector3(-1.0, 1.0, 0.0))
            write(Vector2(1.0, 0.0))

            write(Vector3(1.0, -1.0, 0.0))
            write(Vector2(0.0, 0.0))

            write(Vector3(1.0, 1.0, 0.0))
            write(Vector2(1.0, 1.0))
        }

        val particles = ParticlesManager(800, geometry, paletteStudio.colors2)

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        println("RELOADED at ${current.format(formatter)}")

        val camera = OrbitalCamera(Vector3.UNIT_Z * 1000.0, Vector3.ZERO, 90.0, 0.01, 10000.0)
        val controls = OrbitalControls(camera, keySpeed = 10.0)

        val gui = GUI()

        gui.add(particles.positionShader)

        extend(paletteStudio)
        extend(controls)
        extend(camera)
        extend(Screenshots())
        extend(gui)
        extend {
            drawer.background(paletteStudio.background)

            particles.draw(drawer, seconds, deltaTime)
        }
    }
}


