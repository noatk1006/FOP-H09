package h09.exceptions.packet;

public class PacketSequenceException extends PacketException {
    public PacketSequenceException(int expected, int got) {
        super("Expected " + expected + ", got " + got);
    }
}
