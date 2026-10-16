package com.eminus.model;

final class TestBakery {
    static ModelBakery start(StateBaker baker) {
        return start(baker, PackBlocks.NONE);
    }

    static ModelBakery start(StateBaker baker, PackBlocks packBlocks) {
        return ModelBakery.start(baker, packBlocks, VanillaMipBlend.BLEND);
    }

    private TestBakery() {
    }
}
