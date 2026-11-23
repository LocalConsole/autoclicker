package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Картинка для карты, которую мы рисуем сами по пикселям и показываем одной текстурой
 * (а не тысячами прямоугольников — так в разы быстрее).
 */
public class MapTexture {
    private final Identifier id;
    private NativeImageBackedTexture tex;
    private int w, h;

    public MapTexture(String name) {
        this.id = Identifier.of(UniversalMod.MOD_ID, name);
    }

    /** Подготовить картинку нужного размера (пересоздаётся только при смене размера). */
    public void ensure(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (tex != null && w == width && h == height) return;
        close();
        w = width;
        h = height;
        tex = new NativeImageBackedTexture(() -> "universal_mod_" + id.getPath(), new NativeImage(w, h, true));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
    }

    public int width() { return w; }
    public int height() { return h; }

    public void set(int x, int y, int argb) {
        NativeImage img = tex.getImage();
        if (img != null) img.setColorArgb(x, y, argb);
    }

    public void upload() {
        if (tex != null) tex.upload();
    }

    /** Нарисовать на экране в прямоугольнике drawW×drawH. */
    public void draw(DrawContext ctx, int x, int y, int drawW, int drawH) {
        if (tex == null) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, drawW, drawH, w, h, w, h);
    }

    public void close() {
        if (tex != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            tex = null;
        }
    }
}
