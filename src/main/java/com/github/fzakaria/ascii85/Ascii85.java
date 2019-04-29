package com.github.fzakaria.ascii85;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

/**
 * A very simple class that helps encode/decode for Ascii85 / base85
 * The version that is likely most similar that is implemented here would be the Adobe version.
 * @see <a href="https://en.wikipedia.org/wiki/Ascii85">Ascii85</a>
 */
public class Ascii85 {

    private final static int ASCII_SHIFT = 33;

    private static int[] BASE85_POW = {
            1,
            85,
            85 * 85,
            85 * 85 * 85,
            85 * 85 * 85 *85
    };

    private Ascii85() {
    }

    public static String encode(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("You must provide a non-null input");
        }
        //By using five ASCII characters to represent four bytes of binary data the encoded size ¹⁄₄ is larger than the original
        StringBuilder stringBuff = new StringBuilder(payload.length * 5/4);
        //We break the payload into int (4 bytes)
        byte[] chunk = new byte[4];
        int chunkIndex = 0;
        for(int i = 0 ; i < payload.length; i++) {
            byte currByte = payload[i];
            chunk[chunkIndex++] = currByte;

            if (chunkIndex == 4) {
                int value = byteToInt(chunk);
                //Because all-zero data is quite common, an exception is made for the sake of data compression,
                //and an all-zero group is encoded as a single character "z" instead of "!!!!!".
                if (value == 0) {
                    stringBuff.append('z');
                } else {
                    stringBuff.append(encodeChunk(value));
                }
                Arrays.fill(chunk, (byte) 0);
                chunkIndex = 0;
            }
        }

        //If we didn't end on 0, then we need some padding
        if (chunkIndex > 0) {
            int numPadded = chunk.length - chunkIndex;
            Arrays.fill(chunk, chunkIndex, chunk.length, (byte)0);
            int value = byteToInt(chunk);
            char[] encodedChunk = encodeChunk(value);
            for(int i = 0 ; i < encodedChunk.length - numPadded; i++) {
                stringBuff.append(encodedChunk[i]);
            }
        }

        return stringBuff.toString();
    }

    private static char[] encodeChunk(int value) {
        //transform value to unsigned long
        long longValue = value & 0x00000000ffffffffL;
        char[] encodedChunk = new char[5];
        for(int i = 0 ; i < encodedChunk.length; i++) {
            encodedChunk[i] = (char) ((longValue / BASE85_POW[4 - i]) + ASCII_SHIFT);
            longValue = longValue % BASE85_POW[4 - i];
        }
        return encodedChunk;
    }

    /**
     * This is a very simple base85 decoder. It respects the 'z' optimization for empty chunks, and
     * strips whitespace between characters to respect line limits.
     * @see <a href="https://en.wikipedia.org/wiki/Ascii85">Ascii85</a>
     * @param chars The input characters that are base85 encoded.
     * @return The binary data decoded from the input
     */
    public static byte[] decode(CharSequence chars) {
        if (chars == null) {
            throw new IllegalArgumentException("You must provide a non-null input");
        }
        //Since Base85 is an ascii encoder, we don't need to get the bytes as UTF-8.
        //1. Whitespace characters may occur anywhere to accommodate line length limitations. So lets strip it.
        IntStream validChars = chars.chars().filter(x -> x == 'z' || (x >= '!' && x <= 'u'));
        
        List<byte[]> decodedChunks = new LinkedList<>();        
        
        PrimitiveIterator.OfInt payload = validChars.iterator();
        byte[] inputChunk = new byte[5];
        int chunkIndex = 0;
        while(payload.hasNext()) {
            byte currByte = payload.next().byteValue();
            //Because all-zero data is quite common, an exception is made for the sake of data compression,
            //and an all-zero group is encoded as a single character "z" instead of "!!!!!".
            if (currByte == 'z') {
                if (chunkIndex > 0) {
                    throw new IllegalArgumentException("The payload is not base 85 encoded.");
                }
                Arrays.fill(inputChunk, (byte) '!');
                chunkIndex += 5;
            } else {
                inputChunk[chunkIndex++] = currByte;
            }

            if (chunkIndex == 5) {
                decodedChunks.add(decodeChunk(inputChunk));
                Arrays.fill(inputChunk, (byte) 0);
                chunkIndex = 0;
            }
        }

        //If we didn't end on 0, then we need some padding
        if (chunkIndex > 0) {
            int numPadded = inputChunk.length - chunkIndex;
            Arrays.fill(inputChunk, chunkIndex, inputChunk.length, (byte)'u');
            byte[] paddedDecode = decodeChunk(inputChunk);
            byte[] result = Arrays.copyOf(paddedDecode, paddedDecode.length - numPadded);
            decodedChunks.add(result);
        }

        //decodedChunks.flip();
        int outputLength = decodedChunks.stream().mapToInt(arr -> arr.length).sum();
        ByteBuffer outputBuffer = ByteBuffer.allocate(outputLength);
        for(byte[] xxx: decodedChunks){
            outputBuffer.put(xxx);
        }
        outputBuffer.flip();
        return Arrays.copyOf(outputBuffer.array(),outputBuffer.limit());
    }

    private static byte[] decodeChunk(byte[] chunk) {
        if (chunk.length != 5) {
            throw new IllegalArgumentException("You can only decode chunks of size 5.");
        }
        int value = 0;
        value += (chunk[0] - ASCII_SHIFT) * BASE85_POW[4];
        value += (chunk[1] - ASCII_SHIFT) * BASE85_POW[3];
        value += (chunk[2] - ASCII_SHIFT) * BASE85_POW[2];
        value += (chunk[3] - ASCII_SHIFT) * BASE85_POW[1];
        value += (chunk[4] - ASCII_SHIFT) * BASE85_POW[0];

        return intToByte(value);
    }

    private static int byteToInt(byte[] value) {
        if (value == null || value.length != 4) {
            throw new IllegalArgumentException("You cannot create an int without exactly 4 bytes.");
        }
        return ByteBuffer.wrap(value).getInt();
    }

    private static byte[] intToByte(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) (value)
        };
    }



}
