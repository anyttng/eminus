package com.eminus.beam;

import java.util.List;

public record Beam(long pos, List<BeamSection> sections) {
}
