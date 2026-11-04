package com.ikae.snowthing.domain.search.semantic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** float32 벡터의 BLOB 변환과 코사인 유사도. */
public final class VectorMath {

    private VectorMath() {}

    /** little-endian float32 배열. 길이는 dimension x 4 바이트다. */
    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer =
                ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("vector byte length is not a multiple of 4");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /** 길이가 다르거나 한쪽이 0 벡터면 0. 합은 double로 계산한다. */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static boolean isFinite(float[] vector) {
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
