package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.ResortResponse;
import com.ikae.snowthing.domain.member.dto.RidingStyleResponse;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.RidingStyleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterDataService {

    private final ResortRepository resortRepository;
    private final RidingStyleRepository ridingStyleRepository;

    public List<ResortResponse> getAllResorts() {
        return resortRepository.findAll().stream()
                .map(ResortResponse::from)
                .toList();
    }

    public List<RidingStyleResponse> getAllRidingStyles() {
        return ridingStyleRepository.findAll().stream()
                .map(RidingStyleResponse::from)
                .toList();
    }
}
