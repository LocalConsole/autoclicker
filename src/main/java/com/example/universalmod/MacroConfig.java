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

public class MacroConfig {
    public static List<MacroBind> binds = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_macros.json");
    private static final Type TYPE = new TypeToken<List<MacroBind>>() {}.getType();

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            List<MacroBind> list = GSON.fromJson(r, TYPE);
            if (list != null) {
                for (MacroBind b : list) {
                    if (b.text == null) b.text = "";
                    if (b.version < MacroBind.CURRENT_VERSION) {
                        // Перенос из старого формата: режим -> повтор / «пока зажато»; выключенный макрос -> без клавиши
                        if (b.mode != null) {
                            b.repeat = b.mode != MacroBind.Mode.ONCE;
                            b.holdOnly = b.mode == MacroBind.Mode.HOLD;
                        }
                        if (Boolean.FALSE.equals(b.enabled)) b.keyCode = -1;
                        b.mode = null;
                        b.enabled = null;
                        b.version = MacroBind.CURRENT_VERSION;
                    }
                }
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
