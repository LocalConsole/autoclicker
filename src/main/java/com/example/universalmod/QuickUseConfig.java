package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuickUseConfig {
    public static List<QuickUseBind> binds = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_quickuse.json");
    private static final Type TYPE = new TypeToken<List<QuickUseBind>>() {}.getType();

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            List<QuickUseBind> list = GSON.fromJson(r, TYPE);
            if (list != null) {
                for (QuickUseBind b : list) if (b.item == null) b.item = "";
                binds = list;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(binds, TYPE, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
