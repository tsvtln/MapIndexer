package xyz.skyfalls.shared;

import java.nio.ByteBuffer;

public class VarInt {
    public static void writeSigned(ByteBuffer buffer, long value) {
        // embed sign-bit in the number, lowest bit in the 1st chunk
        writeUnsigned(buffer, (value << 1) ^ (value >> 63));
    }

    public static void writeUnsigned(ByteBuffer buffer, long value) {
        // split into 7 bit chunks, little endian
        while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buffer.put((byte) (value & 0x7F));
    }

    public static long readSignedVarLong(ByteBuffer buffer) {
        long raw = readUnsignedVarLong(buffer);
        // This undoes the trick in writeSignedVarLong()
        long temp = ((raw << 63) >> 63) ^ (raw >> 1);
        // This extra step lets us deal with the largest signed values by treating
        // negative results from read unsigned methods as like unsigned values
        // Must re-flip the top bit if the original read value had it set.
        return temp ^ (raw & (1L << 63));
    }

    public static long readUnsignedVarLong(ByteBuffer buffer) {
        long value = 0L;
        int i = 0;
        long b;
        while (((b = buffer.get()) & 0b1000_0000) != 0) {
            value |= (b & 0b111_1111) << i;
            i += 7;
        }
        return value | (b << i);
    }
}
