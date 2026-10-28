package com.example.universalmod;

/** Свободный осмотр: камера крутится вокруг игрока, сам игрок смотрит и ходит как раньше. */
public class FreeLook {
    public static boolean active = false;
    public static float camYaw = 0;
    public static float camPitch = 0;

    /** Поворот камеры от движения мыши (те же коэффициенты, что у игрока в ванили). */
    public static void turn(double dx, double dy) {
        camYaw += (float) (dx * 0.15);
        camPitch = Math.max(-90f, Math.min(90f, camPitch + (float) (dy * 0.15)));
    }
}
