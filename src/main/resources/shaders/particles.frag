#version 330

in vec2 v_texCoord0;
out vec4 o_output;

uniform sampler2D tex0;
uniform sampler2D tex1;

void main() {
    vec3 pos = texture(tex0, v_texCoord0).rgb;
    vec3 vel = texture(tex1, v_texCoord0).rgb;
    vel = vel * 2.0 - 1.0;

    pos += vel * 0.001;

    o_output = vec4(pos, 1.0);
}
