package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Check the sky light that {@link ClonedChunkSection} builds for an all-air section against vanilla
 * {@link Chunk#getSavedLightValue}.
 */
class EmptySectionSkyLightFixTest {

    private static final int SECTION_Y = 2;
    private static final int SECTION_BLOCK_LENGTH = 16;
    private static final int COLUMNS = SECTION_BLOCK_LENGTH * SECTION_BLOCK_LENGTH;
    private static final int SECTION_FLOOR_Y = ChunkSectionPos.getBlockCoord(SECTION_Y);
    // A column's height map is one above the column's highest light-blocking block. An all-air section has no
    // light-blocking block, so no column's height map is from SECTION_FLOOR_Y + 1 to SECTION_FLOOR_Y + 16.
    private static final int LOWEST_HEIGHT_MAP_ABOVE_SECTION = SECTION_FLOOR_Y + SECTION_BLOCK_LENGTH + 1;

    @ParameterizedTest
    @MethodSource("generateHeightMapsAboveSectionFloorInSomeColumn")
    void sectionBelowHeightMapGetsVanillaSkyLight(int[] heightMap) throws ReflectiveOperationException {
        final Chunk chunk = buildChunk(heightMap);

        final ExtendedBlockStorage section = buildEmptySectionWithSkyLight(chunk);
        final ExtendedBlockStorageExt sectionCopy = new ExtendedBlockStorageExt(chunk, section);

        assertNotNull(section.getSkylightArray());
        assertTrue(sectionCopy.hasSky);
        assertNotNull(sectionCopy.getSkylightArray());
        for (int ly = 0; ly < SECTION_BLOCK_LENGTH; ly++) {
            for (int lz = 0; lz < SECTION_BLOCK_LENGTH; lz++) {
                for (int lx = 0; lx < SECTION_BLOCK_LENGTH; lx++) {
                    final int vanillaSkyLight = chunk
                        .getSavedLightValue(EnumSkyBlock.Sky, lx, SECTION_FLOOR_Y + ly, lz);
                    assertEquals(vanillaSkyLight, section.getExtSkylightValue(lx, ly, lz));
                    assertEquals(vanillaSkyLight, sectionCopy.getSkylightArray().get(lx, ly, lz));
                }
            }
        }
    }

    @Test
    void sectionAboveHeightMapKeepsEmptySection() throws ReflectiveOperationException {
        final Chunk chunk = buildChunk(new int[COLUMNS]);

        final ExtendedBlockStorage section = buildEmptySectionWithSkyLight(chunk);

        assertSame(readEmptySection(), section);
        assertNull(section.getSkylightArray());
    }

    static Stream<int[]> generateHeightMapsAboveSectionFloorInSomeColumn() {
        final int RANDOM_MAPS = 8;
        final long RANDOM_SEED = 2050;

        final int[] coveredEverywhere = new int[COLUMNS];
        Arrays.fill(coveredEverywhere, LOWEST_HEIGHT_MAP_ABOVE_SECTION);
        final Random random = new Random(RANDOM_SEED);
        final Stream<int[]> randomMaps = Stream.generate(() -> generateHeightMap(random)).limit(RANDOM_MAPS);
        return Stream.concat(Stream.of(coveredEverywhere), randomMaps);
    }

    private static int[] generateHeightMap(Random random) {
        final int MAX_HEIGHT_MAP = 256;

        final int[] heightMap = new int[COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            final int openHeight = random.nextInt(SECTION_FLOOR_Y + 1);
            final int coveringHeight = LOWEST_HEIGHT_MAP_ABOVE_SECTION
                + random.nextInt(MAX_HEIGHT_MAP - LOWEST_HEIGHT_MAP_ABOVE_SECTION + 1);
            heightMap[column] = random.nextBoolean() ? openHeight : coveringHeight;
        }
        return heightMap;
    }

    private static Chunk buildChunk(int[] heightMap) {
        // Chunk.getSavedLightValue() reads worldObj only for a non-null section, and every section of this chunk is
        // null.
        final Chunk chunk = new Chunk(null, 0, 0);
        System.arraycopy(heightMap, 0, chunk.heightMap, 0, COLUMNS);
        return chunk;
    }

    private static ExtendedBlockStorage buildEmptySectionWithSkyLight(Chunk chunk)
        throws ReflectiveOperationException {
        final Method buildMethod = ClonedChunkSection.class
            .getDeclaredMethod("buildEmptySectionWithSkyLight", Chunk.class, ChunkSectionPos.class);
        buildMethod.setAccessible(true);
        return (ExtendedBlockStorage) buildMethod.invoke(null, chunk, ChunkSectionPos.from(0, SECTION_Y, 0));
    }

    private static ExtendedBlockStorage readEmptySection() throws ReflectiveOperationException {
        final Field emptySectionField = ClonedChunkSection.class.getDeclaredField("EMPTY_SECTION");
        emptySectionField.setAccessible(true);
        return (ExtendedBlockStorage) emptySectionField.get(null);
    }
}
