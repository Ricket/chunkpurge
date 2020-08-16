package org.richardcarter.chunkpurge;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import net.minecraft.util.math.ChunkPos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WorldChunkUnloaderTest {

    @Test
    public void groupedChunksFinder_diagonalExcluded() {
        ImmutableSet<ChunkPos> loadedChunks = ImmutableSet.<ChunkPos>builder()
                .add(new ChunkPos(1, 1))
                .addAll(makeRectangle(new ChunkPos(2, 2), new ChunkPos(4, 4)))
                .build();

        ChunkPos seed = new ChunkPos(3, 3);
        Set<ChunkPos> grouped = WorldChunkUnloader.groupedChunksFinder(loadedChunks, seed, 0);
        assertTrue(loadedChunks.containsAll(grouped));
        Set<ChunkPos> excluded = Sets.difference(loadedChunks, grouped);
        assertEquals(Collections.singleton(new ChunkPos(1, 1)), excluded);
    }

    @Test
    public void groupedChunksFinder_radiusLimit() {
        ImmutableSet<ChunkPos> loadedChunks = ImmutableSet.<ChunkPos>builder()
                .addAll(makeRectangle(new ChunkPos(1, 1), new ChunkPos(4, 4)))
                .build();

        ChunkPos seed = new ChunkPos(3, 3);
        Set<ChunkPos> grouped = WorldChunkUnloader.groupedChunksFinder(loadedChunks, seed, 1);
        assertTrue(loadedChunks.containsAll(grouped));
        Set<ChunkPos> excluded = Sets.difference(loadedChunks, grouped);
        Set<ChunkPos> expectedExcluded = Sets.difference(loadedChunks, makeRectangle(new ChunkPos(2, 2), new ChunkPos(4, 4)));
        assertEquals(7, expectedExcluded.size());
        assertEquals(expectedExcluded, excluded);
    }

    @Test
    public void groupedChunksFinder_nonRectangle() {
        testWithStrings("x", new ChunkPos(0, 0), 1, " ");
        testWithStrings("xxxxx", new ChunkPos(2, 0), 1, "x   x");
        testWithStrings("xxxxx", new ChunkPos(2, 0), 2, "     ");
        testWithStrings("xxxxx", new ChunkPos(2, 0), 10, "     ");

        String loaded = newlines(
                "         x",
                " xxxxxxx x",
                "    xxx  x",
                "   xxxxx x",
                "     x   x",
                "     x   x",
                "    xxx  x"
        );
        String expected = newlines(
                "         x",
                "         x",
                "         x",
                "         x",
                "         x",
                "         x",
                "         x"
        );
        testWithStrings(loaded, new ChunkPos(3, 3), 10, expected);
    }

    private String newlines(String... lines) {
        return Joiner.on('\n').join(lines);
    }

    private void testWithStrings(String loaded, ChunkPos seed, int radius, String expected) {
        int[] testNums = {0, -1, -5, -100, 1, 5, 100};

        for (int x : testNums) {
            for (int z : testNums) {
                testWithStrings(loaded, seed, radius, expected, x, z);
            }
        }
    }

    private void testWithStrings(String loaded, ChunkPos seedRelative, int radius, String expected, int x, int z) {
        assertEquals(loaded.length(), expected.length());
        Set<ChunkPos> loadedChunks = makeSetFromString(loaded, x, z);
        ChunkPos seed = new ChunkPos(seedRelative.x + x, seedRelative.z + z);
        Set<ChunkPos> grouped = WorldChunkUnloader.groupedChunksFinder(loadedChunks, seed, radius);
        assertTrue(loadedChunks.containsAll(grouped));
        Set<ChunkPos> expectedExcluded = makeSetFromString(expected, x, z);
        Set<ChunkPos> excluded = Sets.difference(loadedChunks, grouped);
        assertChunkSetsEqual(expectedExcluded, excluded, x, z);
    }

    private void assertChunkSetsEqual(Set<ChunkPos> expected, Set<ChunkPos> actual, int minX, int minZ) {
        assertEquals("Expected:\n" + makeStringFromSet(expected, minX, minZ) +
                "\nbut was:\n" + makeStringFromSet(actual, minX, minZ),
                expected, actual);
    }

    private Set<ChunkPos> makeSetFromString(String s, int x, int z) {
        final int initialX = x;

        Builder<ChunkPos> builder = ImmutableSet.builder();
        for (int cIdx = 0; cIdx < s.length(); cIdx++) {
            char c = s.charAt(cIdx);
            if (c == 'x') {
                builder.add(new ChunkPos(x, z));
                x++;
            } else if (c == ' ') {
                x++;
            } else if (c == '\n') {
                x = initialX;
                z++;
            } else {
                fail("Unknown char " + c);
            }
        }
        return builder.build();
    }

    @Test
    public void testMakeStringFromSet() {
        assertThat(makeStringFromSet(makeChunkPosSet(new int[][] {{0, 0}, {1, 0}}), 0, 0))
                .isEqualTo("xx\n");
        assertThat(makeStringFromSet(makeChunkPosSet(new int[][] {{1, 0}, {1, 1}}), 0, 0))
                .isEqualTo(" x\n x\n");
        assertThat(makeStringFromSet(makeChunkPosSet(new int[][] {{0, 1}, {1, 1}}), 0, 0))
                .isEqualTo("\nxx\n");
        assertThat(makeStringFromSet(makeChunkPosSet(new int[][] {{0, 0}, {0, 1}}), 0, 0))
                .isEqualTo("x\nx\n");
    }

    private String makeStringFromSet(Set<ChunkPos> chunks, int minX, int minZ) {
        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();

        Multimap<Integer, ChunkPos> chunksByZ = chunks.stream()
                .collect(Multimaps.toMultimap(c -> c.z, c -> c, HashMultimap::create));

        List<Integer> zs = new ArrayList<>(chunksByZ.keySet());
        Collections.sort(zs);

        assertTrue(zs.get(0) >= minZ);
        for (int z = minZ; z <= zs.get(zs.size() - 1); z++) {
            Collection<ChunkPos> chunksInThisZ = chunksByZ.get(z);

            List<ChunkPos> sortedByX = chunksInThisZ.stream()
                    .sorted(Comparator.comparingInt(o -> o.x))
                    .collect(Collectors.toList());
            for (int x = minX, byXIdx = 0; byXIdx < sortedByX.size(); byXIdx++) {
                ChunkPos chunk = sortedByX.get(byXIdx);
                if (chunk.x > x) {
                    stringBuilder.append(Strings.repeat(" ", chunk.x - x));
                }
                stringBuilder.append('x');
                x = chunk.x + 1;
            }

            stringBuilder.append('\n');
        }
        String result = stringBuilder.toString();
        assertEquals(chunks.size(), result.chars().filter(c -> c == 'x').count());
        return result;
    }

    private Set<ChunkPos> makeChunkPosSet(int[][] arr) {
        Builder<ChunkPos> builder = ImmutableSet.builder();
        for (int[] ints : arr) {
            builder.add(new ChunkPos(ints[0], ints[1]));
        }
        return builder.build();
    }

    private Set<ChunkPos> makeRectangle(ChunkPos corner1, ChunkPos corner2) {
        // (corners are inclusive)
        int minX = Math.min(corner1.x, corner2.x);
        int maxX = Math.max(corner1.x, corner2.x);
        int minZ = Math.min(corner1.z, corner2.z);
        int maxZ = Math.max(corner1.z, corner2.z);

        Builder<ChunkPos> builder = ImmutableSet.builder();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                builder.add(new ChunkPos(x, z));
            }
        }
        return builder.build();
    }
}