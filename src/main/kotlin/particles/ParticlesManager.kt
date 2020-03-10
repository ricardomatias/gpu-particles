package particles

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.glslify.preprocessGlslify
import org.openrndr.extra.noise.Random
import org.openrndr.extra.parameters.Description
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extras.camera.OrbitalCamera
import org.openrndr.math.Quaternion
import org.openrndr.math.Spherical
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix
import org.openrndr.math.transforms.transform
import paletteStudio
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val posShader = """
    #version 330 core

    in vec2 v_texCoord0;

    uniform sampler2D tex0;
    uniform sampler2D tex1;
    uniform sampler2D tex2;
    
    uniform float uTime;
    uniform float uNoiseScale;
    uniform float uNoiseTime;
    uniform float uAgeLimit;
    uniform float deltaTime;
    
#pragma glslify: snoise = require(glsl-noise/simplex/4d)

    vec3 snoiseVec3( vec3 x, float time ){
      float s  = snoise(vec4(vec3( x ), time));
      float s1 = snoise(vec4(vec3( x.y - 19.1 , x.z + 33.4 , x.x + 47.2 ), time));
      float s2 = snoise(vec4(vec3( x.z + 74.2 , x.x - 124.5 , x.y + 99.4 ), time));
      return vec3( s , s1 , s2 );
    }
    
    vec3 curlNoise( vec3 p, float time ){
      const float e = .1;
      vec3 dx = vec3( e   , 0.0 , 0.0 );
      vec3 dy = vec3( 0.0 , e   , 0.0 );
      vec3 dz = vec3( 0.0 , 0.0 , e   );
    
      vec3 p_x0 = snoiseVec3( p - dx , time);
      vec3 p_x1 = snoiseVec3( p + dx , time);
      vec3 p_y0 = snoiseVec3( p - dy , time);
      vec3 p_y1 = snoiseVec3( p + dy , time);
      vec3 p_z0 = snoiseVec3( p - dz , time);
      vec3 p_z1 = snoiseVec3( p + dz , time);
    
      float x = p_y1.z - p_y0.z - p_z1.y + p_z0.y;
      float y = p_z1.x - p_z0.x - p_x1.z + p_x0.z;
      float z = p_x1.y - p_x0.y - p_y1.x + p_y0.x;
    
      const float divisor = 1.0 / ( 2.0 * e );
      return normalize( vec3( x , y , z ) * divisor );
    }
    
    float rand(vec2 co){
      return abs(fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453));
    }

    // NOISE END
    layout(location = 0) out vec4 o_position;
    layout(location = 1) out vec4 o_velocity;
    layout(location = 2) out vec4 o_info;

    void main() {
        vec2 uv = v_texCoord0;
        ivec2 size = textureSize(tex0, 0);
        vec4 particle = texelFetch(tex0, ivec2(uv * size), 0);
        vec3 pos = particle.xyz;
        
        vec3 vel = texelFetch(tex1, ivec2(uv * size), 0).rgb;
        
        vec4 particleInfo = texelFetch(tex2, ivec2(uv * size), 0);
        float age = particleInfo.x * uAgeLimit;
        float instance = particleInfo.y;
        
        float maxFriction = 0.9;
        float friction = particleInfo.z * maxFriction;

        vec3 noise = curlNoise(pos.xyz * uNoiseScale, uTime * uNoiseTime);

        if (age > uAgeLimit) {
            pos = noise * rand(uv) * 10.0;
            age = rand(uv + instance + uTime) * uAgeLimit;
        }

        vel += noise * deltaTime;
        pos += vel;
        vel *= 0.8;
        
        age += deltaTime;

        o_position = vec4(pos, 1.0);
        o_velocity = vec4(vel, 1.0);
        o_info = vec4(age / uAgeLimit, instance, 0.0, 1.0);
    }
""".trimIndent()

val posShaderCode = preprocessGlslify(posShader)

@Description("Particles")
class PositionShader : Filter(filterShaderFromCode(posShaderCode)) {
    var range: Double by parameters
    var deltaTime: Double by parameters
    var uTime: Double by parameters
    @DoubleParameter("Noise scale", 0.01, 0.25, 3)
    var uNoiseScale: Double by parameters
    @DoubleParameter("Noise Time", 0.01, 1.0, 2)
    var uNoiseTime: Double by parameters
    @DoubleParameter("Max Age", 0.1, 20.0, 2)
    var uAgeLimit: Double by parameters

    init {
        uNoiseScale = 0.05
        uNoiseTime = 0.1
        uAgeLimit = 5.0
    }
}

class ParticlesManager(val particleRes: Int, val geometry: VertexBuffer) {
    val particleCount = particleRes * particleRes

    val positionsBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)
    val velocityBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)
    val infoBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)
    val rotationBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)

    val transforms: VertexBuffer
    var colors: VertexBuffer

    val positionShader: PositionShader = PositionShader()

    init {
        positionsBuffer.filterMag = MagnifyingFilter.NEAREST
        positionsBuffer.wrapU = WrapMode.CLAMP_TO_EDGE
        positionsBuffer.wrapV = WrapMode.CLAMP_TO_EDGE

        fillBuffer(positionsBuffer) { x: Int, y: Int ->
            val pos = Random.Vector3(-10.0, 10.0)
            Vector4(pos.x, pos.y, pos.z, 1.0)
        }

        fillBuffer(infoBuffer) { x: Int, y: Int ->
            val instance = x + y * particleRes.toDouble()
            val age = Random.double0()
            val friction = Random.double(0.2, 0.9)
            Vector4(age, instance, friction, 1.0)
        }

        fillBuffer(velocityBuffer) { x: Int, y: Int ->
            val vel = Random.Vector3()
            Vector4(vel.x, vel.y, vel.z, 1.0)
        }

        fillBuffer(rotationBuffer) { x: Int, y: Int ->
            val sphere = Spherical(1.0, Random.double0(PI), Random.double0(PI * 2.0)).cartesian

            Vector4(sphere.x, sphere.y, sphere.z, 0.0)
        }

        // -- create the secondary vertex buffer, which will hold transformations
        transforms = vertexBuffer(vertexFormat {
            attribute("scale", VertexElementType.MATRIX44_FLOAT32)
//            attribute("color", VertexElementType.VECTOR3_FLOAT32)
        }, particleCount)

        // -- fill the transform buffer
        transforms.put {
            for (i in 0 until particleCount) {
                write(transform {
                    scale(1.0)
                })
//                val color = Random.pick(colors).toLinear()
//                write(Vector3(color.r, color.g, color.b))
            }
        }

        colors = vertexBuffer(vertexFormat {
            attribute("colors", VertexElementType.VECTOR4_FLOAT32, paletteStudio.colors2.size)
        }, particleCount)

        colors.put {
            for (i in 0 until particleCount) {
                for (c in paletteStudio.colors2) {
                    write(c)
                }
            }
        }

        paletteStudio.onChange {
            colors = vertexBuffer(vertexFormat {
                attribute("colors", VertexElementType.VECTOR4_FLOAT32, paletteStudio.colors2.size)
            }, particleCount)

            colors.put {
                for (i in 0 until particleCount) {
                    for (c in paletteStudio.colors2) {
                        write(c)
                    }
                }
            }
        }
    }

    fun draw(
        drawer: Drawer,
        time: Double,
        deltaTime: Double,
        range: Double,
        alpha: Double
    ) {
        drawer.isolated {
            drawer.shadeStyle = shadeStyle {
                vertexPreamble = """
                    out float age;
                    out vec4 color;
                """.trimIndent()
                vertexTransform = """
                    vec4 pos = texelFetch(p_posTex, ivec2(c_instance % p_res, int(c_instance / p_res)), 0);
                    vec4 info = texelFetch(p_infoTex, ivec2(c_instance % p_res, int(c_instance / p_res)), 0);
                    
                    float dist = distance(pos.xyz, vec3(0.0));
                    
                    pos *= p_range;
                    
                    age = info.x;

                    mat4 translation = mat4(1.0);
                    translation[3] = vec4(pos.xyz, 1.0);
                    
                    mat4 rot = mat4(1.0);
                    
                    // Column 0:
                    rot[0][0] = p_cameraWorldSpace[0][0];
                    rot[0][1] = p_cameraWorldSpace[0][1];
                    rot[0][2] = p_cameraWorldSpace[0][2];
                    
                    // Column 1:
                    rot[1][0] = p_cameraWorldSpace[1][0];
                    rot[1][1] = p_cameraWorldSpace[1][1];
                    rot[1][2] = p_cameraWorldSpace[1][2];
                    
                    // Column 2:
                    rot[2][0] = p_cameraWorldSpace[2][0];
                    rot[2][1] = p_cameraWorldSpace[2][1];
                    rot[2][2] = p_cameraWorldSpace[2][2];
                    
                    color = i_colors[int(floor((info.y / p_count) * p_colorsSize))];
                    
                    x_viewMatrix = p_viewMatrix;
                    x_modelMatrix = translation * rot * i_scale;
                """.trimIndent()
                fragmentPreamble = """
                    in float age;
                    in vec4 color;
                """.trimIndent()
                fragmentTransform = """
                    float dist = distance(v_worldPosition, vec3(0.0));
//                    x_fill.rgb = vi_colors[int(mod((dist / pow(p_range, 2.0)) , 4.0))].rgb;
                    x_fill.rgb = color.rgb;
                    x_fill.a = mix(p_alpha, 0.0, pow(age, 2.0));
                """.trimIndent()
                parameter("res", particleRes)
                parameter("posTex", positionsBuffer)
                parameter("rotTex", rotationBuffer)
                parameter("infoTex", infoBuffer)
                parameter("TAU", 2.0 * PI)
                parameter("time", time)
                parameter("count", particleCount)
                parameter("colorsSize", paletteStudio.colors2.size)
                parameter("ageLimit", time)
                parameter("range", range)
                parameter("alpha", alpha)
                parameter("viewMatrix", drawer.view)
                parameter("cameraWorldSpace", drawer.view.inversed)
                attributes(colors)
            }
            drawer.drawStyle.depthTestPass = DepthTestPass.ALWAYS
            drawer.vertexBufferInstances(listOf(geometry), listOf(transforms), DrawPrimitive.TRIANGLE_STRIP, particleCount)
        }

        positionShader.deltaTime = deltaTime
        positionShader.uTime = time

        positionShader.apply(arrayOf(positionsBuffer, velocityBuffer, infoBuffer), arrayOf(positionsBuffer, velocityBuffer, infoBuffer))

//        drawer.isolated {
//            drawer.defaults()
//            drawer.image(infoBuffer, width - 200.0, 0.0, 200.0, 200.0)
//            drawer.image(velocityBuffer, width - 200.0, 200.0, 200.0, 200.0)
//        }
    }
}