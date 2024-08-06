package xyz.skyfalls.shared.api;

import xyz.skyfalls.shared.abstraction.Offer;
import xyz.skyfalls.shared.abstraction.FreeBlockPos;

import java.util.List;

public record Submission(FreeBlockPos position, String dimension, String owner, List<Offer> offers) {
}
