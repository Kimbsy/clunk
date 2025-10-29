#version 330 core
layout (location = 0) in vec3 aPos; // the position input variable has attribute position 0

uniform mat4 uOrthoProjection;
uniform mat4 uModel;

void main()
{
    gl_Position = uOrthoProjection * uModel * vec4(aPos, 1.0);
}
