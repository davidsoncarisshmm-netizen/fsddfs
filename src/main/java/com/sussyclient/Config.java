package com.sussyclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static Config I = new Config();

    // Sus Chunk Finder
    public boolean enabled = false;
    public int sensitivity = 3;          // min fully grown clusters in a chunk to flag it
    public int simDistance = 12;         // chunks around you to scan
    public int maxY = 0;                 // only count clusters below this Y
    public int scanSpeed = 4;            // chunks scanned per tick
    public int boxHeight = 64;           // blocks tall, starting at world bottom
    public int chunkSize = 100;          // % of the chunk footprint
    public float outlineThickness = 0.15f;
    public int fillOpacity = 25;         // %
    public int red = 255, green = 60, blue = 60;
    public boolean outline = true;
    public boolean fill = true;
    public boolean throughWalls = true;
    public boolean chatNotify = true;


    // Activity Inspector
    public boolean aiEnabled = false;
    public int aiSensitivity = 2;        // min "unnatural" blocks around a spawner to flag the chunk
    public int aiRadius = 12;            // chunks to scan
    public int aiScanSpeed = 4;
    public int aiMaxY = 64;              // only spawners below this Y
    public int aiSlabY = 62;
    public float aiSlabThickness = 1.0f; // blocks
    public int aiSlabSize = 100;         // % of chunk
    public int aiOpacity = 40;           // %
    public int aiRed = 255, aiGreen = 200, aiBlue = 0;
    public boolean aiOutline = true;
    public boolean aiFill = true;
    public boolean aiThroughWalls = true;
    public boolean aiChat = true;
    public boolean aiCoords = true;
    public int rtpRegion = 0;            // 0 east,1 west,2 eu west,3 eu central,4 asia,5 oceania
    public int rtpDelay = 100;           // ticks between /rtp
    public boolean crownEnabled = false;
    public String crownUsername = "";    // edit in config/sussyclient.json
    public boolean crownFeedback = true;
    public int crownTimeout = 30;        // seconds
    public boolean rtpAfterTimeout = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("sussyclient.json"); }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        try { Files.writeString(path(), GSON.toJson(I)); } catch (Exception e) { e.printStackTrace(); }
    }
}
