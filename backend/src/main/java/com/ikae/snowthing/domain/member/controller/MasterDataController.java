package com.ikae.snowthing.domain.member.controller;

import com.ikae.snowthing.domain.member.dto.ResortResponse;
import com.ikae.snowthing.domain.member.dto.RidingStyleResponse;
import com.ikae.snowthing.domain.member.service.MasterDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/master")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    @GetMapping("/resorts")
    public ResponseEntity<List<ResortResponse>> getResorts() {
        return ResponseEntity.ok(masterDataService.getAllResorts());
    }

    @GetMapping("/riding-styles")
    public ResponseEntity<List<RidingStyleResponse>> getRidingStyles() {
        return ResponseEntity.ok(masterDataService.getAllRidingStyles());
    }
}
