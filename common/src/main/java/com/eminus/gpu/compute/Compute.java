package com.eminus.gpu.compute;

import com.eminus.gpu.pass.Bindings;
import com.eminus.gpu.pipeline.Pipeline;

public interface Compute {
    Pipeline pipeline(ComputeSpec spec);

    Pass pass(String label);

    interface Pass extends Bindings, AutoCloseable {
        void pipeline(Pipeline pipeline);

        void dispatch(int groupsX, int groupsY, int groupsZ);

        @Override
        void close();
    }
}
