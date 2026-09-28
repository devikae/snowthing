package com.ikae.snowthing.domain.resortcam.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortcam.dto.ResortCameraGroupResponse;
import com.ikae.snowthing.domain.resortcam.dto.ResortCameraItemResponse;
import com.ikae.snowthing.domain.resortcam.dto.ResortCameraListResponse;
import com.ikae.snowthing.domain.resortcam.repository.ResortCameraRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResortCameraQueryService {

    private final ResortRepository resortRepository;
    private final ResortCameraRepository resortCameraRepository;

    public ResortCameraListResponse getActiveResortCameras() {
        Map<String, List<ResortCameraItemResponse>> camerasByResort =
                resortCameraRepository.findAllActiveOrdered().stream()
                        .collect(
                                Collectors.groupingBy(
                                        camera -> camera.getResort().getCode(),
                                        Collectors.mapping(
                                                ResortCameraItemResponse::from,
                                                Collectors.toList())));

        List<ResortCameraGroupResponse> resorts =
                resortRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .filter(resort -> camerasByResort.containsKey(resort.getCode()))
                        .map(resort -> toResponse(resort, camerasByResort))
                        .toList();
        return new ResortCameraListResponse(resorts);
    }

    private ResortCameraGroupResponse toResponse(
            Resort resort, Map<String, List<ResortCameraItemResponse>> camerasByResort) {
        return new ResortCameraGroupResponse(
                resort.getCode(),
                resort.getName(),
                resort.getRegionName(),
                resort.getDisplayOrder(),
                camerasByResort.getOrDefault(resort.getCode(), List.of()));
    }
}
