package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Check the sky light of a section with no blocks against {@link Chunk#getSavedLightValue} for a null section. */
class EmptySectionSkyLightFixTest {

    @Test
    void emptySectionSkyLightMatchesVanillaNullSection() throws ReflectiveOperationException {
        final int SECTION_BLOCK_LENGTH = 16;
        final ChunkSectionPos pos = ChunkSectionPos.from(0, 2, 0);
        final int minY = pos.getMinY();
        final Chunk chunk = new Chunk(null, 0, 0);
        // A height map value is the y above the column's highest block with a light opacity above 0. In a section
        // with no blocks, the value is at most minY or above minY + 16.
        final int[] heightMapValues = { 0, minY, minY + 17, 256 };
        for (int column = 0; column < chunk.heightMap.length; column++) {
            chunk.heightMap[column] = heightMapValues[column % heightMapValues.length];
        }

        final ExtendedBlockStorageExt clonedSection = new ExtendedBlockStorageExt(
            chunk,
            buildEmptySectionWithSkyLight(chunk, pos));

        assertTrue(clonedSection.hasSky);
        for (int lz = 0; lz < SECTION_BLOCK_LENGTH; lz++) {
            for (int lx = 0; lx < SECTION_BLOCK_LENGTH; lx++) {
                for (int ly = 0; ly < SECTION_BLOCK_LENGTH; ly++) {
                    assertEquals(
                        chunk.getSavedLightValue(EnumSkyBlock.Sky, lx, minY + ly, lz),
                        clonedSection.getExtSkylightValue(lx, ly, lz));
                }
            }
        }
    }

    @Test
    void skyExposedEmptySectionIsSharedEmptySection() throws ReflectiveOperationException {
        // a new Chunk's height map is 0 in every column, so canBlockSeeTheSky() returns true at every y
        final Chunk chunk = new Chunk(null, 0, 0);

        assertSame(readEmptySection(), buildEmptySectionWithSkyLight(chunk, ChunkSectionPos.from(0, 15, 0)));
    }

    private static ExtendedBlockStorage buildEmptySectionWithSkyLight(Chunk chunk, ChunkSectionPos pos)
        throws ReflectiveOperationException {
        final Method buildMethod = ClonedChunkSection.class
            .getDeclaredMethod("buildEmptySectionWithSkyLight", Chunk.class, ChunkSectionPos.class);
        buildMethod.setAccessible(true);
        return (ExtendedBlockStorage) buildMethod.invoke(null, chunk, pos);
    }

    private static ExtendedBlockStorage readEmptySection() throws ReflectiveOperationException {
        final Field emptySectionField = ClonedChunkSection.class.getDeclaredField("EMPTY_SECTION");
        emptySectionField.setAccessible(true);
        return (ExtendedBlockStorage) emptySectionField.get(null);
    }
}
