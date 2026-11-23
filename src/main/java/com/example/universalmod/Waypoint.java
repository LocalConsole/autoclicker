package com.example.universalmod;

/** Одна метка: название, координаты, цвет (индекс в Waypoints.COLORS) и измерение. */
public class Waypoint {
    public String name = "";
    public int x, y, z;
    public int color = 0;
    public String dim = "minecraft:overworld";
    public boolean enabled = true;
    /** Сервер или одиночный мир, где стоит метка (null — старая метка, ещё без мира). */
    public String world = null;

    public Waypoint() {
    }

    public Waypoint(String name, int x, int y, int z, int color, String dim) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.dim = dim;
        this.world = Waypoints.worldKey(net.minecraft.client.MinecraftClient.getInstance());
    }
}
