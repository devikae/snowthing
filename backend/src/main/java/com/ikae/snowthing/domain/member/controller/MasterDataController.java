package com.ikae.snowthing.domain.member.controller;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.RidingStyle;
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
    public ResponseEntity<List<Resort>> getResorts() {
        return ResponseEntity.ok(resortRepository.findAll());
    }

    @GetMapping("/riding-styles")
    public ResponseEntity<List<RidingStyle>> getRidingStyles() {
        return ResponseEntity.ok(ridingStyleRepository.findAll());
    }
}
