package com.ikae.snowthing.domain.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.admin.entity.SystemConfig;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {}
