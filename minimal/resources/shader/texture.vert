#version 330 core
layout (location = 0) in vec3 aPos; // the position input variable has attribute position 0
layout (location = 1) in vec2 aTexCoord; // the texture coordinate input variable has attribute position 1

uniform mat4 uOrthoProjection;

out vec2 texCoord;

void main()
{
    gl_Position = uOrthoProjection * vec4(aPos, 1.0);
    texCoord = aTexCoord;
}
