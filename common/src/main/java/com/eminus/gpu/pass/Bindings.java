package com.eminus.gpu.pass;

import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;

public interface Bindings {
    void bind(String name, Buffer uniform);

    void bind(String name, TexelView texels);

    void bind(String name, Texture texture, Sampler sampler);
}
