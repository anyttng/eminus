package com.eminus.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.texture.Texture;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelSource;

import org.junit.jupiter.api.Test;

class ModelPublisherTest {
    private static final int MODELS = 3000;
    private static final int MAX_TEXTURE_SIDE = 16384;
    private static final int OPAQUE = 0xFF00_0000;
    private static final int CALL_CEILING = 1_000_000;
    private static final int START_CELLS = ModelPublisher.START_ATLAS_CELLS_PER_SIDE;
    private static final int START_MODELS = START_CELLS * START_CELLS / BakedModel.FACE_COUNT;

    @Test
    void oneFrameLeavesWorkForTheNext() {
        RecordingGpu recording = new RecordingGpu();
        ModelPublisher publisher = ModelPublisher.start(recording.gpu(), models(MODELS), 0);

        publisher.publish();

        assertTrue(publisher.published() < MODELS);
        assertFalse(publisher.covers(MODELS));
    }

    @Test
    void everyPublishedModelIsInTheAtlasAfterEveryFrame() {
        RecordingGpu recording = new RecordingGpu();
        ModelPublisher publisher = ModelPublisher.start(recording.gpu(), models(MODELS), 0);

        int calls = 0;
        while (!publisher.covers(MODELS) && calls < CALL_CEILING) {
            publisher.publish();
            calls++;
            assertAtlasHolds(recording, publisher);
        }

        assertEquals(MODELS, publisher.published());
    }

    @Test
    void theAtlasGrowsOnceToFitWhatIsBaked() {
        RecordingGpu recording = new RecordingGpu();
        ModelPublisher publisher = ModelPublisher.start(recording.gpu(), models(MODELS), 0);

        publisher.publishAll();

        assertEquals(MODELS, publisher.published());
        assertEquals(1, publisher.growths());
        assertEquals(START_MODELS, publisher.reuploaded());
        assertAtlasHolds(recording, publisher);
    }

    @Test
    void modelsBakedAfterTheAtlasFilledAreMovedWithTheOnesBefore() {
        RecordingGpu recording = new RecordingGpu();
        List<BakedModel> baked = new ArrayList<>();
        ModelPublisher publisher = ModelPublisher.start(recording.gpu(), source(baked), 0);
        int first = START_MODELS;
        addModels(baked, first);
        publisher.publishAll();
        Texture small = publisher.atlas().color();

        addModels(baked, 1);
        publisher.publishAll();

        assertEquals(first + 1, publisher.published());
        assertEquals(first, publisher.reuploaded());
        assertTrue(recording.closed(small));
        assertAtlasHolds(recording, publisher);
    }

    private static void assertAtlasHolds(RecordingGpu recording, ModelPublisher publisher) {
        Texture color = publisher.atlas().color();
        assertFalse(recording.closed(color));
        Set<Integer> cells = recording.cells(color);
        for (int cell = 0; cell < publisher.published() * BakedModel.FACE_COUNT; cell++) {
            assertTrue(cells.contains(cell), "cell " + cell + " of " + publisher.published() + " published models");
        }
    }

    private static ModelSource models(int count) {
        List<BakedModel> baked = new ArrayList<>();
        addModels(baked, count);
        return source(baked);
    }

    private static void addModels(List<BakedModel> baked, int count) {
        for (int added = 0; added < count; added++) {
            baked.add(BakedModel.solid(OPAQUE | baked.size()));
        }
    }

    private static ModelSource source(List<BakedModel> baked) {
        return new ModelSource() {
            @Override
            public int modelCount() {
                return baked.size();
            }

            @Override
            public BakedModel model(int modelId) {
                return baked.get(modelId);
            }
        };
    }

    private static final class RecordingGpu {
        private final Map<Texture, Set<Integer>> cells = new HashMap<>();
        private final Set<Texture> closed = new HashSet<>();

        Gpu gpu() {
            return (Gpu) Proxy.newProxyInstance(Gpu.class.getClassLoader(), new Class<?>[] {Gpu.class},
                    (proxy, method, arguments) -> answer(method, arguments));
        }

        Set<Integer> cells(Texture texture) {
            return cells.getOrDefault(texture, Set.of());
        }

        boolean closed(Texture texture) {
            return closed.contains(texture);
        }

        private Object answer(Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "assertRenderThread" -> null;
                case "maxTextureSide" -> MAX_TEXTURE_SIDE;
                case "buffer" -> stub(Buffer.class);
                case "texelView" -> stub(TexelView.class);
                case "texture" -> new RecordedTexture((Format) arguments[2], (int) arguments[3]);
                case "write" -> {
                    if (arguments[0] instanceof Texture texture && (int) arguments[1] == 0) {
                        int cellsPerSide = texture.width() / BakedModel.FACE_SIDE;
                        int cell = (int) arguments[3] / BakedModel.FACE_SIDE * cellsPerSide
                                + (int) arguments[2] / BakedModel.FACE_SIDE;
                        cells.computeIfAbsent(texture, written -> new HashSet<>()).add(cell);
                    }

                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private static <T> T stub(Class<T> type) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, arguments) -> method.getReturnType() == long.class ? (Object) 0L : null));
        }

        private final class RecordedTexture implements Texture {
            private final Format format;
            private final int side;

            private RecordedTexture(Format format, int side) {
                this.format = format;
                this.side = side;
            }

            @Override
            public int width() {
                return side;
            }

            @Override
            public int height() {
                return side;
            }

            @Override
            public Format format() {
                return format;
            }

            @Override
            public void close() {
                closed.add(this);
            }
        }
    }
}
