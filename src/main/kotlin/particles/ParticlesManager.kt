package particles

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.glslify.preprocessGlslify
import org.openrndr.extra.noise.Random
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Spherical
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
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
    
    uniform float uTime;
    uniform float uNoiseScale;
    uniform float uNoiseTime;
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
    
    float rand(vec2 n) { 
	    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
    }

    // NOISE END
    layout(location = 0) out vec4 o_position;
    layout(location = 1) out vec4 o_velocity;

    void main() {
        vec4 particle = texture(tex0, v_texCoord0).rgba;
        vec3 pos = particle.xyz;
        float age = particle.w;
        
        vec3 vel = texture(tex1, v_texCoord0).rgb;

        vec3 noise = curlNoise(pos.xyz * uNoiseScale, uTime * uNoiseTime);

        if (age == 0.0) {
            age = rand(pos.xy + vel.xy) * 10.0;
        }
        if (age > 10.0) {
            pos = noise * 10.0;
            age = 0.0;
        }

        vel += noise * deltaTime;
        pos += vel;
        vel *= 0.8;
        
        age += deltaTime;

        o_position = vec4(pos, age);
        o_velocity = vec4(vel, 1.0);
    }
""".trimIndent()

val posShaderCode = preprocessGlslify(posShader)

class PositionShader : Filter(filterShaderFromCode(posShaderCode)) {
    var range: Double by parameters
    var deltaTime: Double by parameters
    var uTime: Double by parameters
    @DoubleParameter("Noise scale", 0.01, 0.25, 3)
    var uNoiseScale: Double by parameters
    @DoubleParameter("Noise Time", 0.01, 1.0, 2)
    var uNoiseTime: Double by parameters

    init {
        uNoiseScale = 0.01
        uNoiseTime = 0.1
    }
}

class ParticlesManager(val particleRes: Int, val geometry: VertexBuffer, colors: List<ColorRGBa>) {
    val particleCount = particleRes * particleRes

    val positionsBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)
    val velocityBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)
    val rotationBuffer = colorBuffer(particleRes, particleRes, type = ColorType.FLOAT32, format = ColorFormat.RGBa)

    val transforms: VertexBuffer

    val positionShader: PositionShader = PositionShader()

    val range = 10.0

    init {
        positionsBuffer.filterMag = MagnifyingFilter.NEAREST
        positionsBuffer.wrapU = WrapMode.CLAMP_TO_EDGE
        positionsBuffer.wrapV = WrapMode.CLAMP_TO_EDGE

        fillBuffer(positionsBuffer) { x: Int, y: Int ->
            val pos = Random.Vector3(-10.0, 10.0)
            Vector4(pos.x, pos.y, pos.z, 0.0)
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
            attribute("color", VertexElementType.VECTOR3_FLOAT32)
        }, particleCount)

        // -- fill the transform buffer
        transforms.put {
            for (i in 0 until particleCount) {
                write(transform {
                    scale(Random.double(1.0, 3.0))
                })
                val color = Random.pick(colors).toLinear()
                write(Vector3(color.r, color.g, color.b))
            }
        }
    }

    fun draw(
        drawer: Drawer,
        time: Double,
        deltaTime: Double
    ) {
        drawer.isolated {
            drawer.fill = paletteStudio.foreground.opacify(0.4)
            drawer.shadeStyle = shadeStyle {
                vertexTransform = """
                    vec4 pos = texelFetch(p_posTex, ivec2(c_instance % p_res, int(c_instance / p_res)), 0);
                    vec4 rot = texelFetch(p_rotTex, ivec2(c_instance % p_res, int(c_instance / p_res)), 0);
                    
                    rot.xyz *= p_TAU;
                    rot.xyz += p_time * 1.0 / (1 + c_instance);
                    
                    pos *= p_range;

                    float x = pos.x;// * p_range;
                    float y = pos.y;// * p_range;
                    float z = pos.z;// * p_range;
                    
                    float cosX = cos(rot.x);
                    float sinX = sin(rot.x);
                    float cosY = cos(rot.y);
                    float sinY = sin(rot.y);
                    float cosZ = cos(rot.z);
                    float sinZ = sin(rot.z);
                    
                    mat4 rotation = mat4(1.0);
                    
                    float m00 = cosY * cosZ + sinX * sinY * sinZ; 
                    float m01 = cosY * sinZ - sinX * sinY * cosZ; 
                    float m02 = cosX * sinY;
                    float m03 = 0.0;
                    
                    float m04 = -cosX * sinZ; 
                    float m05 = cosX * cosZ; 
                    float m06 = sinX;
                    float m07 = 0.0;
                    
                    float m08 = sinX * cosY * sinZ - sinY * cosZ;
                    float m09 = -sinY * sinZ - sinX * cosY * cosZ;
                    float m10 = cosX * cosY;
                    float m11 = 0.0;
                    
                    //------ Orientation ---------------------------------
                    rotation[0] = vec4(m00, m01, m02, m03); // first column.
                    rotation[1] = vec4(m04, m05, m06, m07); // second column.
                    rotation[2] = vec4(m08, m09, m10, m11); // third column.

                    mat4 translation = mat4(1.0);
                    translation[3] = vec4(x, y, z, 1.0);

                    x_viewMatrix = p_viewMatrix;
                    x_modelMatrix = translation * rotation * i_scale;
                    
                    gl_PointSize = 100.0;
                """.trimIndent()
                parameter("res", particleRes)
                parameter("posTex", positionsBuffer)
                parameter("rotTex", rotationBuffer)
                parameter("TAU", 2.0 * PI)
                parameter("time", time)
                parameter("range", range)
                parameter("viewMatrix", drawer.view)
            }
            drawer.vertexBufferInstances(listOf(geometry), listOf(transforms), DrawPrimitive.TRIANGLE_STRIP, particleCount)
        }

        positionShader.deltaTime = deltaTime
        positionShader.uTime = time

        positionShader.apply(arrayOf(positionsBuffer, velocityBuffer), arrayOf(positionsBuffer, velocityBuffer))

//        drawer.isolated {
//            drawer.defaults()
//            drawer.image(positionsBuffer, width - 200.0, 0.0, 200.0, 200.0)
//            drawer.image(velocityBuffer, width - 200.0, 200.0, 200.0, 200.0)
//        }
    }
}