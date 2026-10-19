package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Check the sky light of a section with no blocks against the sky light of vanilla Minecraft. */
class EmptySectionSkyLightFixTest {

    private static final long SEED = 2050L;
    private static final int GENERATED_CHUNKS = 200;
    private static final int SECTION_BLOCK_LENGTH = 16;
    private static final int SECTIONS_PER_CHUNK = 16;
    private static final int MAX_HEIGHT_MAP = 256;

    @Test
    void skyLightOfEmptySectionMatchesVanilla() throws ReflectiveOperationException {
        final Random random = new Random(SEED);
        for (int chunkIndex = 0; chunkIndex < GENERATED_CHUNKS; chunkIndex++) {
            final int sectionY = random.nextInt(SECTIONS_PER_CHUNK);
            final int floorY = ChunkSectionPos.getBlockCoord(sectionY);
            assertSkyLightMatchesVanilla(newChunk(column -> pickHeightOutsideSection(random, floorY)), sectionY);
        }

        for (int sectionY = 0; sectionY < SECTIONS_PER_CHUNK; sectionY++) {
            final int floorY = ChunkSectionPos.getBlockCoord(sectionY);
            final int aboveTopY = floorY + SECTION_BLOCK_LENGTH;
            assertSkyLightMatchesVanilla(newChunk(column -> floorY), sectionY);
            assertSkyLightMatchesVanilla(newChunk(column -> aboveTopY), sectionY);
            assertSkyLightMatchesVanilla(newChunk(column -> column % 2 == 0 ? floorY : aboveTopY), sectionY);
        }
    }

    @Test
    void emptySectionUnderOpenSkyHasNoSkyLightArray() throws ReflectiveOperationException {
        final Random random = new Random(SEED);
        for (int chunkIndex = 0; chunkIndex < GENERATED_CHUNKS; chunkIndex++) {
            final int sectionY = random.nextInt(SECTIONS_PER_CHUNK);
            final int floorY = ChunkSectionPos.getBlockCoord(sectionY);
            final Chunk chunk = newChunk(column -> random.nextInt(floorY + 1));
            assertNull(buildEmptySection(chunk, sectionY).getSkylightArray());
        }

        for (int sectionY = 0; sectionY < SECTIONS_PER_CHUNK; sectionY++) {
            final int floorY = ChunkSectionPos.getBlockCoord(sectionY);
            assertNull(buildEmptySection(newChunk(column -> floorY), sectionY).getSkylightArray());
            assertNull(buildEmptySection(newChunk(column -> 0), sectionY).getSkylightArray());
        }
    }

    private static void assertSkyLightMatchesVanilla(Chunk chunk, int sectionY) throws ReflectiveOperationException {
        final ExtendedBlockStorageExt copy = new ExtendedBlockStorageExt(chunk, buildEmptySection(chunk, sectionY));
        // ClonedChunkSection.getLightArray() returns null for a copy whose `hasSky` is false
        final NibbleArray skyLightArray = copy.hasSky ? copy.getSkylightArray() : null;
        final int floorY = ChunkSectionPos.getBlockCoord(sectionY);
        for (int x = 0; x < SECTION_BLOCK_LENGTH; x++) {
            for (int y = 0; y < SECTION_BLOCK_LENGTH; y++) {
                for (int z = 0; z < SECTION_BLOCK_LENGTH; z++) {
                    // In a world with sky, WorldSlice.getLightLevel() returns EnumSkyBlock.Sky.defaultLightValue for
                    // a null array.
                    final int drawnSkyLight = skyLightArray == null
                        ? EnumSkyBlock.Sky.defaultLightValue
                        : skyLightArray.get(x, y, z);
                    final int blockY = floorY + y;
                    assertEquals(
                        chunk.getSavedLightValue(EnumSkyBlock.Sky, x, blockY, z),
                        drawnSkyLight,
                        "sky light at x=" + x + " y=" + blockY + " z=" + z);
                }
            }
        }
    }

    private static Chunk newChunk(IntUnaryOperator heightOfColumn) {
        // With no sections, getSavedLightValue() and canBlockSeeTheSky() read only the height map and never the
        // world.
        final Chunk chunk = new Chunk(null, 0, 0);
        for (int column = 0; column < chunk.heightMap.length; column++) {
            chunk.heightMap[column] = heightOfColumn.applyAsInt(column);
        }
        return chunk;
    }

    private static int pickHeightOutsideSection(Random random, int floorY) {
        // The height map of a column is 1 above the column's highest block with a light opacity above 0.
        // A section with no blocks has no such block, so the height map is at or below the floor, or at or above
        // the floor + 16.
        final int aboveTopY = floorY + SECTION_BLOCK_LENGTH;
        if (random.nextBoolean() || aboveTopY > MAX_HEIGHT_MAP) {
            return random.nextInt(floorY + 1);
        }
        return aboveTopY + random.nextInt(MAX_HEIGHT_MAP - aboveTopY + 1);
    }

    private static ExtendedBlockStorage buildEmptySection(Chunk chunk, int sectionY)
        throws ReflectiveOperationException {
        final Method buildMethod = ClonedChunkSection.class
            .getDeclaredMethod("buildEmptySectionWithSkyLight", Chunk.class, ChunkSectionPos.class);
        buildMethod.setAccessible(true);
        return (ExtendedBlockStorage) buildMethod.invoke(null, chunk, ChunkSectionPos.from(0, sectionY, 0));
    }
}
