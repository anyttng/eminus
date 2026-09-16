package com.eminus.work;

import java.util.Arrays;
import java.util.List;

final class ServiceSelector {
    static final int MIN_SHARE_PERCENT = 5;
    static final int NONE = -1;

    interface Selectable {
        int weight();

        boolean ready();
    }

    private int[] credits = new int[0];

    int pick(List<? extends Selectable> services) {
        if (credits.length < services.size()) {
            credits = Arrays.copyOf(credits, services.size());
        }

        int readyWeight = 0;
        for (Selectable service : services) {
            if (service.ready()) {
                readyWeight += service.weight();
            }
        }

        if (readyWeight == 0) {
            return NONE;
        }

        int floor = minimumShare(readyWeight);
        int total = 0;
        int best = NONE;

        for (int index = 0; index < services.size(); index++) {
            Selectable service = services.get(index);
            if (!service.ready()) {
                continue;
            }

            int effective = Math.max(service.weight(), floor);
            credits[index] += effective;
            total += effective;

            if (best == NONE || credits[index] > credits[best]) {
                best = index;
            }
        }

        credits[best] -= total;
        return best;
    }

    static int minimumShare(int readyWeight) {
        return Math.ceilDiv(readyWeight * MIN_SHARE_PERCENT, 100);
    }
}
