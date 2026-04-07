package devflow.agent.parsing;

public record ByteRange(
        int startByte,
        int endByte
) {
    public boolean isValid() {
        return startByte >= 0 && endByte >= startByte;
    }
}
