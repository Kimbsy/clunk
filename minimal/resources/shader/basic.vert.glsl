#version 330 core
layout (location = 0) in vec3 aPos; // the position input variable has attribute position 0
layout (location = 1) in vec3 aColor; // the color input variable has attribute position 1

out vec3 color;

void main()
{
    gl_Position = vec4(aPos.x, aPos.y, aPos.z, 1.0);
    color = aColor;
}
