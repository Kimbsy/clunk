#version 330 core

in vec2 vTexCoord;
in vec3 vColour;

uniform sampler2D uTexture;

out vec4 FragColor;

void main()
{
    FragColor = texture(uTexture, vTexCoord) * vec4(vColour, 1.0);
}
