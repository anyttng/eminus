package com.eminus.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ServiceSelectorTest {
    private static final class Fake implements ServiceSelector.Selectable {
        private final int weight;
        private boolean ready = true;

        private Fake(int weight) {
            this.weight = weight;
        }

        @Override
        public int weight() {
            return weight;
        }

        @Override
        public boolean ready() {
            return ready;
        }
    }

    private static int longestGap(ServiceSelector selector, List<Fake> services, int index, int picks) {
        int gap = 0;
        int longest = 0;

        for (int pick = 0; pick < picks; pick++) {
            if (selector.pick(services) == index) {
                gap = 0;
            } else {
                gap++;
                longest = Math.max(longest, gap);
            }
        }

        return longest;
    }

    private static int countOf(ServiceSelector selector, List<Fake> services, int index, int picks) {
        int count = 0;

        for (int pick = 0; pick < picks; pick++) {
            if (selector.pick(services) == index) {
                count++;
            }
        }

        return count;
    }

    @Test
    void picksAreSharedByWeightOverAFullPeriod() {
        List<Fake> services = List.of(new Fake(10), new Fake(1));

        assertEquals(1, countOf(new ServiceSelector(), services, 1, 11));
        assertEquals(10, countOf(new ServiceSelector(), services, 0, 11));
    }

    @Test
    void theLightServiceIsNeverPassedOverForLongerThanThePeriod() {
        List<Fake> services = List.of(new Fake(10), new Fake(1));

        assertTrue(longestGap(new ServiceSelector(), services, 1, 110) < 11);
    }

    @Test
    void theMinimumShareKeepsAThousandToOneServiceOffZero() {
        List<Fake> services = List.of(new Fake(1000), new Fake(1));
        int floor = ServiceSelector.minimumShare(1001);

        assertEquals(51, floor);
        assertEquals(floor, countOf(new ServiceSelector(), services, 1, 1000 + floor));
    }

    @Test
    void aServiceThatIsNotReadyIsSkipped() {
        Fake heavy = new Fake(10);
        Fake light = new Fake(1);
        List<Fake> services = List.of(heavy, light);
        heavy.ready = false;

        assertEquals(11, countOf(new ServiceSelector(), services, 1, 11));
    }

    @Test
    void nothingIsPickedWhenNoServiceIsReady() {
        Fake service = new Fake(10);
        service.ready = false;

        assertEquals(ServiceSelector.NONE, new ServiceSelector().pick(List.of(service)));
    }
}
