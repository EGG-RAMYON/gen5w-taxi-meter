package dev.gen5w.taxi;

final class SpeedIntervals {
    static final long MAX_GAP_MS = 1000;
    private long previous = -1;

    static float parse(String raw) {
        if (raw == null || "-".equals(raw)) return Float.NaN;
        try {
            float speed = Float.parseFloat(raw);
            return speed >= 0 && speed <= 300 ? speed : Float.NaN;
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    long accept(float kmh, long now) {
        long gap = now - previous;
        boolean continuous = previous >= 0 && gap > 0 && gap <= MAX_GAP_MS;
        boolean valid = !Float.isNaN(kmh);
        previous = valid ? now : -1;
        return valid && continuous ? gap : 0;
    }

    void reset() { previous = -1; }
}
