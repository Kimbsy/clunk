#version 330 core
layout (location = 0) in vec3 aPos; // the position input variable has attribute position 0

uniform mat4 uOrthoProjection;

out vec4 vClip;
out vec3 vWorld;

void main()
{
    vec4 clip = uOrthoProjection * vec4(aPos, 1.0);
    vClip = clip;
    vWorld = aPos;
    gl_Position = clip;
}
