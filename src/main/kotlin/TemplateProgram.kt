import particles.ParticlesManager
import org.openrndr.application
import org.openrndr.draw.*
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.palette.PaletteStudio
import org.openrndr.extra.parameters.Description
import org.openrndr.extra.parameters.DoubleParameter
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

        val particles = ParticlesManager(500, geometry, paletteStudio.colors2)

        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        println("RELOADED at ${current.format(formatter)}")

        val camera = OrbitalCamera(Vector3.UNIT_Z * 1000.0, Vector3.ZERO, 45.0, 0.01, 5000.0)
        val controls = OrbitalControls(camera, keySpeed = 10.0)

        val gui = GUI()

        val params = @Description("Settings") object {
            @DoubleParameter("Max alpha", 0.01, 1.0, 2)
            var alpha = 0.4
            @DoubleParameter("Particles range", 1.0, 100.0, 1)
            var range = 10.0
        }

        gui.compartmentsCollapsedByDefault = false

        gui.add(params)
        gui.add(particles.positionShader)

        extend(paletteStudio)
        extend(controls)
        extend(camera)
        extend(Screenshots())
        extend(gui)
        extend {
            drawer.background(paletteStudio.background)
            camera.rotate(deltaTime * 7.5, 0.0)

            particles.draw(drawer, camera, seconds, deltaTime, params.range, params.alpha)
        }
    }
}


