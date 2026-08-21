package com.ikae.snowthing.domain.member.controller;

import com.ikae.snowthing.domain.member.dto.ResortResponse;
import com.ikae.snowthing.domain.member.dto.RidingStyleResponse;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.RidingStyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MasterDataController {

    private final ResortRepository resortRepository;
    private final RidingStyleRepository ridingStyleRepository;

    @GetMapping("/resorts")
    public ResponseEntity<List<ResortResponse>> getResorts() {
        List<ResortResponse> response = resortRepository.findAll().stream()
                .map(ResortResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/riding-styles")
    public ResponseEntity<List<RidingStyleResponse>> getRidingStyles() {
        List<RidingStyleResponse> response = ridingStyleRepository.findAll().stream()
                .map(RidingStyleResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
