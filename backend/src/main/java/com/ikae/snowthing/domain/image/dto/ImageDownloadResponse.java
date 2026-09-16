package com.ikae.snowthing.domain.image.dto;

public record ImageDownloadResponse(byte[] data, String contentType) {
    public ImageDownloadResponse {
        data = data != null ? data.clone() : new byte[0];
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
