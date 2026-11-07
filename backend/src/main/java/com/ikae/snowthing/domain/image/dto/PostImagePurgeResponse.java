package com.ikae.snowthing.domain.image.dto;

import java.util.List;

/**
 * @param queuedKeys 약 1분 안에 정리 작업이 지울 파일
 * @param sharedKeys 다른 글도 쓰고 있어 지우지 않는 파일. 그 글의 사진도 지워야 파일이 사라진다.
 */
public record PostImagePurgeResponse(List<String> queuedKeys, List<String> sharedKeys) {}
