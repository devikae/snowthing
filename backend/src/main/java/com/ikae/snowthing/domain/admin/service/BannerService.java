package com.ikae.snowthing.domain.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.BannerCreateRequest;
import com.ikae.snowthing.domain.admin.dto.BannerResponse;
import com.ikae.snowthing.domain.admin.dto.BannerUpdateRequest;
import com.ikae.snowthing.domain.admin.entity.Banner;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.BannerRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository repository;
    private final AdminActionLogger actionLogger;

    @Transactional(readOnly = true)
    public List<BannerResponse> getBanners(boolean publicOnly) {
        List<Banner> banners =
                publicOnly
                        ? repository.findByIsActiveTrueOrderByDisplayOrderAscCreatedAtDesc()
                        : repository.findAllByOrderByDisplayOrderAscCreatedAtDesc();
        return List.copyOf(banners.stream().map(BannerResponse::from).toList());
    }

    @Transactional
    public BannerResponse createBanner(BannerCreateRequest req) {
        Banner banner =
                Banner.builder()
                        .title(req.title())
                        .imageUrl(req.imageUrl())
                        .linkUrl(req.linkUrl())
                        .displayOrder(req.displayOrder())
                        .isActive(req.isActive())
                        .startAt(req.startAt())
                        .endAt(req.endAt())
                        .build();

        Banner saved = repository.save(banner);
        actionLogger.record(
                AdminActionType.BANNER_CREATE, AdminTargetType.BANNER, saved.getId(), null);
        log.info("Banner created: id={}, title={}", saved.getId(), saved.getTitle());
        return BannerResponse.from(saved);
    }

    @Transactional
    public BannerResponse updateBanner(Long id, BannerUpdateRequest req) {
        Banner banner =
                repository
                        .findById(id)
                        .orElseThrow(() -> new CustomException(ErrorCode.BANNER_NOT_FOUND));

        banner.update(
                req.title(),
                req.imageUrl(),
                req.linkUrl(),
                req.displayOrder(),
                req.isActive(),
                req.startAt(),
                req.endAt());
        actionLogger.record(AdminActionType.BANNER_UPDATE, AdminTargetType.BANNER, id, null);
        log.info("Banner updated: id={}, title={}", banner.getId(), banner.getTitle());
        return BannerResponse.from(banner);
    }

    @Transactional
    public void deleteBanner(Long id) {
        Banner banner =
                repository
                        .findById(id)
                        .orElseThrow(() -> new CustomException(ErrorCode.BANNER_NOT_FOUND));

        repository.delete(banner);
        actionLogger.record(AdminActionType.BANNER_DELETE, AdminTargetType.BANNER, id, null);
        log.info("Banner deleted: id={}", id);
    }

    @Transactional
    public void toggleActive(Long id, boolean active) {
        Banner banner =
                repository
                        .findById(id)
                        .orElseThrow(() -> new CustomException(ErrorCode.BANNER_NOT_FOUND));

        banner.toggleActive(active);
        log.info("Banner toggled: id={}, active={}", id, active);
    }
}
