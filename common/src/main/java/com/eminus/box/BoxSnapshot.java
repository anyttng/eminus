package com.eminus.box;

import java.util.List;

import com.eminus.api.v1.FarBox;

public record BoxSnapshot(long version, List<FarBox> boxes) {
}
