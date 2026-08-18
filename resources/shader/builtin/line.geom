#version 330 core
layout (lines) in;
layout (triangle_strip, max_vertices = 4) out;

in vec4 vClip[]; // clip-space positions from vertex shader
in vec3 vWorld[]; // original world positions

uniform mat4 uOrthoProjection;
uniform vec2 uViewport;
uniform float uThickness;

vec2 clipToScreen(vec4 clip) {
    // clip.xy / clip.w -> NDC [-1,1]
    vec2 ndc = clip.xy / clip.w;
    // map NPC [-1,1] -> screen [0,width],[0,height]
    return ((ndc * 0.5) + 0.5) * uViewport;
}

vec4 screenToClip(vec2 screen, float w, float z) {
    // screen -> NDC
    vec2 ndc = (screen / uViewport - 0.5) * 2.0;
    // reconstruct clip pos (x,y,z,w)
    return vec4(ndc * w, z, w);
}

void emitQuadVertex(vec2 screenPos, float w, float z) {
    vec4 clip = screenToClip(screenPos, w, z);
    gl_Position = clip;
    EmitVertex();
}

void main() {
    // clip-space positions for the two ends of the line
    vec4 c0 = vClip[0];
    vec4 c1 = vClip[1];

    // convert to screen coords (pixels)
    vec2 s0 = clipToScreen(c0);
    vec2 s1 = clipToScreen(c1);

    // calualate direction vector (in screen space)
    vec2 dir = s1 - s0;
    float len = length(dir);

    // tiny line, just draw a tiny square so something is visible
    if (len <= 1e-6) {
        vec2 off = vec2(uThickness * 0.5, 0.0);
        emitQuadVertex(s0 - off, c0.w, c0.z);
        emitQuadVertex(s0 + off, c0.w, c0.z);
        EndPrimitive();
        return;
    }

    // calculate unit perpendicular vector (in screen space)
    vec2 n = normalize(vec2(-dir.y, dir.x));

    // offset in pixels
    float halfThickness = uThickness * 0.5;
    vec2 off = n * halfThickness;

    // create quad corners (triangle strip order: v0, v1, v2, v3)
    // v0 = p0 + off
    // v1 = p0 - off
    // v2 = p1 + off
    // v3 = p1 - off
    vec2 v0 = s0 + off;
    vec2 v1 = s0 - off;
    vec2 v2 = s1 + off;
    vec2 v3 = s1 - off;

    // emit quad vertices
    emitQuadVertex(v0, c0.w, c0.z);
    emitQuadVertex(v1, c0.w, c0.z);
    emitQuadVertex(v2, c1.w, c1.z);
    emitQuadVertex(v3, c1.w, c1.z);

    EndPrimitive();
}
