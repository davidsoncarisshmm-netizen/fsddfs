package com.sussyclient;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

/**
 * Activity Inspector (original Sussy Client implementation).
 * Flags chunks that contain a mob spawner surrounded by player-made blocks
 * (torches, lanterns, hoppers, glass, planks), marks them with a slab, and can
 * run an /rtp loop that stops when something is found.
 */
public class ActivityInspector {
    public static final String[] REGIONS = {"east", "west", "eu west", "eu central", "asia", "oceania"};
    private static final int ROOM_RADIUS = 6;

    private static final Map<Long, Integer> scores = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();
    private static final List<int[]> queue = new ArrayList<>();
    private static int idx = 0, lastCX = Integer.MIN_VALUE, lastCZ = Integer.MIN_VALUE, lastRadius = -1;

    // RTP loop state
    private static boolean rtpRunning = false;
    private static int rtpTimer = 0;
    private static boolean crownWaiting = false;
    private static int crownWait = 0;

    public static boolean isRtpRunning() { return rtpRunning; }
    public static void setRtpRunning(boolean on) {
        rtpRunning = on;
        rtpTimer = 0;
        crownWaiting = false;
    }

    private static long key(int x, int z) { return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32); }
    private static int keyX(long k) { return (int) (k & 0xFFFFFFFFL); }
    private static int keyZ(long k) { return (int) (k >>> 32); }

    // ---------------- tick ----------------
    public static void tick(Minecraft mc) {
        Config c = Config.I;
        if (!c.aiEnabled || mc.level == null || mc.player == null) {
            if (!scores.isEmpty() || !queue.isEmpty()) { scores.clear(); notified.clear(); queue.clear(); idx = 0; lastRadius = -1; }
            rtpRunning = false; crownWaiting = false;
            return;
        }

        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.isEmpty() || idx >= queue.size() || pcx != lastCX || pcz != lastCZ || c.aiRadius != lastRadius) {
            rebuild(pcx, pcz, c.aiRadius);
        }

        for (int n = 0; n < c.aiScanSpeed && idx < queue.size(); n++, idx++) {
            int[] cp = queue.get(idx);
            long k = key(cp[0], cp[1]);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp[0], cp[1]);
            if (chunk == null) { scores.remove(k); notified.remove(k); continue; }

            int score = scan(chunk, mc.level, c);
            scores.put(k, score);

            if (score >= c.aiSensitivity) {
                if (notified.add(k)) onSpotted(mc, cp[0], cp[1], score);
            } else {
                notified.remove(k);
            }
        }

        // RTP loop
        if (crownWaiting) {
            if (--crownWait <= 0) { crownWaiting = false; rtpRunning = true; rtpTimer = 0; }
        } else if (rtpRunning) {
            if (--rtpTimer <= 0) {
                mc.player.connection.sendCommand("rtp " + REGIONS[Math.max(0, Math.min(REGIONS.length - 1, c.rtpRegion))]);
                rtpTimer = c.rtpDelay;
            }
        }
    }

    private static void onSpotted(Minecraft mc, int cx, int cz, int score) {
        Config c = Config.I;
        if (c.aiChat) {
            String where = c.aiCoords ? " at " + (cx * 16 + 8) + ", " + (cz * 16 + 8) : "";
            mc.player.displayClientMessage(Component.literal("§6[Activity] §fSpawner room spotted" + where + " §7(score " + score + ")"), false);
        }
        if (rtpRunning) {
            rtpRunning = false; // stop the loop when something is found
            String name = c.crownUsername == null ? "" : c.crownUsername.trim();
            if (c.crownEnabled && !name.isEmpty()) {
                mc.player.connection.sendCommand("tpahere " + name);
                if (c.crownFeedback)
                    mc.player.displayClientMessage(Component.literal("§6[Activity] §fSent /tpahere to " + name), false);
                if (c.rtpAfterTimeout) { crownWaiting = true; crownWait = Math.max(1, c.crownTimeout) * 20; }
            } else if (c.crownFeedback) {
                mc.player.displayClientMessage(Component.literal("§6[Activity] §fRTP loop stopped."), false);
            }
        }
    }

    private static void rebuild(int pcx, int pcz, int r) {
        queue.clear();
        idx = 0;
        lastCX = pcx; lastCZ = pcz; lastRadius = r;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                if (x * x + z * z <= r * r) queue.add(new int[]{pcx + x, pcz + z});
        queue.sort(Comparator.comparingInt(p -> (p[0] - pcx) * (p[0] - pcx) + (p[1] - pcz) * (p[1] - pcz)));
        scores.keySet().removeIf(k -> {
            int dx = keyX(k) - pcx, dz = keyZ(k) - pcz;
            return dx * dx + dz * dz > r * r;
        });
        notified.retainAll(scores.keySet());
    }

    /** Best "player-made blocks near a spawner" count in this chunk (0 if no spawner). */
    private static int scan(LevelChunk chunk, ClientLevel level, Config c) {
        int best = 0;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            if (!(e.getValue() instanceof SpawnerBlockEntity)) continue;
            BlockPos p = e.getKey();
            if (p.getY() >= c.aiMaxY) continue;
            int score = 0;
            for (int dx = -ROOM_RADIUS; dx <= ROOM_RADIUS; dx++)
                for (int dy = -ROOM_RADIUS; dy <= ROOM_RADIUS; dy++)
                    for (int dz = -ROOM_RADIUS; dz <= ROOM_RADIUS; dz++) {
                        mut.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                        if (isPlayerMade(level.getBlockState(mut))) score++;
                    }
            best = Math.max(best, score);
        }
        return best;
    }

    private static boolean isPlayerMade(BlockState s) {
        return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.LANTERN)
                || s.is(Blocks.HOPPER) || s.is(Blocks.GLASS) || s.is(BlockTags.PLANKS);
    }

    // ---------------- rendering ----------------
    private static final RenderPipeline THROUGH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(SussyClient.ID, "pipeline/activity_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final RenderPipeline DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(SussyClient.ID, "pipeline/activity_depth"))
                    .withCull(false)
                    .build());

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    public static void render(WorldRenderContext context) {
        Config c = Config.I;
        Minecraft mc = Minecraft.getInstance();
        if (!c.aiEnabled || mc.level == null || scores.isEmpty()) return;

        RenderPipeline pipeline = c.aiThroughWalls ? THROUGH : DEPTH;
        PoseStack matrices = context.matrices();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4fc m = matrices.last().pose();

        float r = c.aiRed / 255f, g = c.aiGreen / 255f, b = c.aiBlue / 255f;
        float a = c.aiOpacity / 100f;
        double margin = (16 - 16 * (c.aiSlabSize / 100.0)) / 2.0;
        double y1 = c.aiSlabY, y2 = y1 + c.aiSlabThickness;
        double t = Math.min(0.15, c.aiSlabThickness);

        for (Map.Entry<Long, Integer> e : scores.entrySet()) {
            if (e.getValue() < c.aiSensitivity) continue;
            if (buffer == null) buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            double x1 = keyX(e.getKey()) * 16 + margin, x2 = keyX(e.getKey()) * 16 + 16 - margin;
            double z1 = keyZ(e.getKey()) * 16 + margin, z2 = keyZ(e.getKey()) * 16 + 16 - margin;
            if (c.aiFill && a > 0) box(m, x1, y1, z1, x2, y2, z2, r, g, b, a);
            if (c.aiOutline) {
                box(m, x1, y1, z1, x2, y2, z1 + t, r, g, b, 1f);
                box(m, x1, y1, z2 - t, x2, y2, z2, r, g, b, 1f);
                box(m, x1, y1, z1, x1 + t, y2, z2, r, g, b, 1f);
                box(m, x2 - t, y1, z1, x2, y2, z2, r, g, b, 1f);
            }
        }
        matrices.popPose();

        if (buffer != null) drawBuffer(mc, pipeline);
    }

    private static void v(Matrix4fc m, double x, double y, double z, float r, float g, float b, float a) {
        buffer.addVertex(m, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    private static void box(Matrix4fc m, double x1, double y1, double z1, double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        v(m, x1, y1, z2, r, g, b, a); v(m, x2, y1, z2, r, g, b, a); v(m, x2, y2, z2, r, g, b, a); v(m, x1, y2, z2, r, g, b, a);
        v(m, x2, y1, z1, r, g, b, a); v(m, x1, y1, z1, r, g, b, a); v(m, x1, y2, z1, r, g, b, a); v(m, x2, y2, z1, r, g, b, a);
        v(m, x1, y1, z1, r, g, b, a); v(m, x1, y1, z2, r, g, b, a); v(m, x1, y2, z2, r, g, b, a); v(m, x1, y2, z1, r, g, b, a);
        v(m, x2, y1, z2, r, g, b, a); v(m, x2, y1, z1, r, g, b, a); v(m, x2, y2, z1, r, g, b, a); v(m, x2, y2, z2, r, g, b, a);
        v(m, x1, y2, z2, r, g, b, a); v(m, x2, y2, z2, r, g, b, a); v(m, x2, y2, z1, r, g, b, a); v(m, x1, y2, z1, r, g, b, a);
        v(m, x1, y1, z1, r, g, b, a); v(m, x2, y1, z1, r, g, b, a); v(m, x2, y1, z2, r, g, b, a); v(m, x1, y1, z2, r, g, b, a);
    }

    private static void drawBuffer(Minecraft client, RenderPipeline pipeline) {
        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState params = built.drawState();
        VertexFormat format = params.format();
        GpuBuffer vertices = upload(params, format, built);
        draw(client, pipeline, built, params, vertices);
        vertexBuffer.rotate();
        buffer = null;
    }

    private static GpuBuffer upload(MeshData.DrawState params, VertexFormat format, MeshData built) {
        int size = params.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> SussyClient.ID + " activity vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData built, MeshData.DrawState params, GpuBuffer vertices) {
        built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
        GpuBuffer indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
        VertexFormat.IndexType indexType = built.drawState().indexType();

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> SussyClient.ID + " activity render",
                client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, params.indexCount(), 1);
        }
        built.close();
    }

    public static void close() {
        allocator.close();
        if (vertexBuffer != null) { vertexBuffer.close(); vertexBuffer = null; }
    }
}
