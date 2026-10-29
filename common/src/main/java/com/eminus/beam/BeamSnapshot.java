package com.eminus.beam;

import java.util.List;

public record BeamSnapshot(long version, List<Beam> beams) {
}
