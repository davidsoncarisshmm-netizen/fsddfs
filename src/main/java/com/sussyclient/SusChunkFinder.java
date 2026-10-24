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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

/** Detects fully grown amethyst clusters below a Y level (amethyst bypass). */
public class SusChunkFinder {
    // ---------------- scanning ----------------
    private static final Map<Long, Integer> counts = new HashMap<>();
    private static final Set<Long> notified = new HashSet<>();
    private static final List<int[]> queue = new ArrayList<>();
    private static int idx = 0, lastCX = Integer.MIN_VALUE, lastCZ = Integer.MIN_VALUE, lastRadius = -1;

    private static long key(int x, int z) { return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32); }
    private static int keyX(long k) { return (int) (k & 0xFFFFFFFFL); }
    private static int keyZ(long k) { return (int) (k >>> 32); }

    public static void tick(Minecraft mc) {
        Config c = Config.I;
        if (!c.enabled || mc.level == null || mc.player == null) {
            if (!counts.isEmpty() || !queue.isEmpty()) { counts.clear(); notified.clear(); queue.clear(); idx = 0; lastRadius = -1; }
            return;
        }

        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.isEmpty() || idx >= queue.size() || pcx != lastCX || pcz != lastCZ || c.simDistance != lastRadius) {
            rebuild(pcx, pcz, c.simDistance);
        }

        for (int n = 0; n < c.scanSpeed && idx < queue.size(); n++, idx++) {
            int[] cp = queue.get(idx);
            long k = key(cp[0], cp[1]);
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp[0], cp[1]);
            if (chunk == null) { counts.remove(k); notified.remove(k); continue; }

            int count = scan(chunk, mc.level, c.maxY);
            counts.put(k, count);

            if (count >= c.sensitivity) {
                if (notified.add(k) && c.chatNotify) {
                    mc.player.displayClientMessage(Component.literal("§c[Sussy] §fSus chunk at " + (cp[0] * 16) + ", " + (cp[1] * 16)
                            + " §7(" + count + " grown clusters)"), false);
                }
            } else {
                notified.remove(k);
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

        counts.keySet().removeIf(k -> {
            int dx = keyX(k) - pcx, dz = keyZ(k) - pcz;
            return dx * dx + dz * dz > r * r;
        });
        notified.retainAll(counts.keySet());
    }

    private static int scan(LevelChunk chunk, ClientLevel level, int maxY) {
        int count = 0;
        LevelChunkSection[] sections = chunk.getSections();
        int bottomSection = level.getMinY() >> 4;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            int baseY = (bottomSection + i) * 16;
            if (baseY >= maxY) break;
            if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.is(Blocks.AMETHYST_CLUSTER))) continue;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++) {
                    if (baseY + y >= maxY) continue;
                    for (int z = 0; z < 16; z++)
                        if (s.getBlockState(x, y, z).is(Blocks.AMETHYST_CLUSTER)) count++;
                }
        }
        return count;
    }

    // ---------------- rendering (custom pipelines, per Fabric 1.21.11 docs) ----------------
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(SussyClient.ID, "pipeline/sus_filled_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final RenderPipeline FILLED_DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(SussyClient.ID, "pipeline/sus_filled_depth"))
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
        if (!c.enabled || mc.level == null || counts.isEmpty()) return;

        RenderPipeline pipeline = c.throughWalls ? FILLED_THROUGH_WALLS : FILLED_DEPTH;

        PoseStack matrices = context.matrices();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4fc m = matrices.last().pose();

        float r = c.red / 255f, g = c.green / 255f, b = c.blue / 255f;
        float fillA = c.fillOpacity / 100f;
        double margin = (16 - 16 * (c.chunkSize / 100.0)) / 2.0;
        double y1 = mc.level.getMinY();
        double y2 = y1 + c.boxHeight;
        double t = c.outlineThickness;
        boolean any = false;

        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            if (e.getValue() < c.sensitivity) continue;
            if (buffer == null) buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            any = true;

            double x1 = keyX(e.getKey()) * 16 + margin, x2 = keyX(e.getKey()) * 16 + 16 - margin;
            double z1 = keyZ(e.getKey()) * 16 + margin, z2 = keyZ(e.getKey()) * 16 + 16 - margin;

            if (c.fill && fillA > 0) box(m, x1, y1, z1, x2, y2, z2, r, g, b, fillA);
            if (c.outline) {
                // 4 vertical pillars
                box(m, x1, y1, z1, x1 + t, y2, z1 + t, r, g, b, 1f);
                box(m, x2 - t, y1, z1, x2, y2, z1 + t, r, g, b, 1f);
                box(m, x1, y1, z2 - t, x1 + t, y2, z2, r, g, b, 1f);
                box(m, x2 - t, y1, z2 - t, x2, y2, z2, r, g, b, 1f);
                // top + bottom frames
                for (double yy : new double[]{y1, y2 - t}) {
                    box(m, x1, yy, z1, x2, yy + t, z1 + t, r, g, b, 1f);
                    box(m, x1, yy, z2 - t, x2, yy + t, z2, r, g, b, 1f);
                    box(m, x1, yy, z1, x1 + t, yy + t, z2, r, g, b, 1f);
                    box(m, x2 - t, yy, z1, x2, yy + t, z2, r, g, b, 1f);
                }
            }
        }
        matrices.popPose();

        if (any && buffer != null) drawBuffer(mc, pipeline);
        else buffer = null;
    }

    private static void v(Matrix4fc m, double x, double y, double z, float r, float g, float b, float a) {
        buffer.addVertex(m, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    private static void box(Matrix4fc m, double x1, double y1, double z1, double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        // front
        v(m, x1, y1, z2, r, g, b, a); v(m, x2, y1, z2, r, g, b, a); v(m, x2, y2, z2, r, g, b, a); v(m, x1, y2, z2, r, g, b, a);
        // back
        v(m, x2, y1, z1, r, g, b, a); v(m, x1, y1, z1, r, g, b, a); v(m, x1, y2, z1, r, g, b, a); v(m, x2, y2, z1, r, g, b, a);
        // left
        v(m, x1, y1, z1, r, g, b, a); v(m, x1, y1, z2, r, g, b, a); v(m, x1, y2, z2, r, g, b, a); v(m, x1, y2, z1, r, g, b, a);
        // right
        v(m, x2, y1, z2, r, g, b, a); v(m, x2, y1, z1, r, g, b, a); v(m, x2, y2, z1, r, g, b, a); v(m, x2, y2, z2, r, g, b, a);
        // top
        v(m, x1, y2, z2, r, g, b, a); v(m, x2, y2, z2, r, g, b, a); v(m, x2, y2, z1, r, g, b, a); v(m, x1, y2, z1, r, g, b, a);
        // bottom
        v(m, x1, y1, z1, r, g, b, a); v(m, x2, y1, z1, r, g, b, a); v(m, x2, y1, z2, r, g, b, a); v(m, x1, y1, z2, r, g, b, a);
    }

    private static void drawBuffer(Minecraft client, RenderPipeline pipeline) {
        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState params = built.drawState();
        VertexFormat format = params.format();

        GpuBuffer vertices = upload(params, format, built);
        draw(client, pipeline, built, params, vertices, format);

        vertexBuffer.rotate();
        buffer = null;
    }

    private static GpuBuffer upload(MeshData.DrawState params, VertexFormat format, MeshData built) {
        int size = params.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> SussyClient.ID + " sus chunk vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData built, MeshData.DrawState params,
                             GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            built.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
            indexType = built.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(params.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> SussyClient.ID + " sus chunk render",
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
