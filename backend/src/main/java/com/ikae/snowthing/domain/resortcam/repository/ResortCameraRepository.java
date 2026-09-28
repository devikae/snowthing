package com.ikae.snowthing.domain.resortcam.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ikae.snowthing.domain.resortcam.entity.ResortCamera;

public interface ResortCameraRepository extends JpaRepository<ResortCamera, Long> {

    @Query(
            """
            select camera
            from ResortCamera camera
            join fetch camera.resort resort
            where camera.active = true and resort.active = true
            order by resort.displayOrder asc, resort.id asc,
                     camera.displayOrder asc, camera.id asc
            """)
    List<ResortCamera> findAllActiveOrdered();
}
