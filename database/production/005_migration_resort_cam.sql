-- ====================================================================
-- Snowthing Production Migration
-- Version: 005
-- Description: 리조트 코드 확장 및 리조트캠 마스터 데이터 추가
-- ====================================================================

ALTER TABLE `resort`
    ADD COLUMN `code` VARCHAR(30) NULL AFTER `resort_id`,
    ADD COLUMN `display_order` INT NOT NULL DEFAULT 0 AFTER `region`,
    ADD COLUMN `is_active` BOOLEAN NOT NULL DEFAULT TRUE AFTER `display_order`;

UPDATE `resort` SET `code` = 'PHOENIX', `display_order` = 1, `is_active` = TRUE WHERE `name` = '휘닉스파크';
UPDATE `resort` SET `code` = 'VIVALDI', `display_order` = 2, `is_active` = TRUE WHERE `name` = '비발디파크';
UPDATE `resort` SET `code` = 'HIGH1', `display_order` = 3, `is_active` = TRUE WHERE `name` = '하이원리조트';
UPDATE `resort` SET `code` = 'YONGPYONG', `display_order` = 4, `is_active` = TRUE WHERE `name` = '모나용평';
UPDATE `resort` SET `code` = 'WELLI_HILLI', `display_order` = 5, `is_active` = TRUE WHERE `name` = '웰리힐리파크';
UPDATE `resort` SET `code` = 'JISAN', `display_order` = 6, `is_active` = TRUE WHERE `name` = '지산리조트';

INSERT INTO `resort` (`code`, `name`, `region`, `display_order`, `is_active`) VALUES
('KONJIAM', '곤지암리조트', '경기 광주', 7, TRUE),
('MUJU', '무주덕유산리조트', '전북 무주', 8, TRUE),
('EDEN_VALLEY', '에덴밸리리조트', '경남 양산', 9, TRUE),
('ELYSIAN', '엘리시안 강촌', '강원 춘천', 10, TRUE),
('ALPENSIA', '알펜시아리조트', '강원 평창', 11, TRUE),
('OAK_VALLEY', '오크밸리', '강원 원주', 12, TRUE),
('O2_RESORT', '오투리조트', '강원 태백', 13, TRUE) 
ON DUPLICATE KEY UPDATE
    `code` = VALUES(`code`),
    `region` = VALUES(`region`),
    `display_order` = VALUES(`display_order`),
    `is_active` = VALUES(`is_active`);

ALTER TABLE `resort`
    MODIFY COLUMN `code` VARCHAR(30) NOT NULL,
    ADD CONSTRAINT `uk_resort_code` UNIQUE (`code`),
    ADD INDEX `idx_resort_active_order` (`is_active`, `display_order`, `resort_id`);

CREATE TABLE `resort_camera` (
    `resort_camera_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `resort_id` BIGINT NOT NULL,
    `code` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `source_type` VARCHAR(20) NOT NULL,
    `source_url` VARCHAR(1000) NOT NULL,
    `external_page_url` VARCHAR(1000) NULL,
    `display_order` INT NOT NULL,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_resort_camera_resort` FOREIGN KEY (`resort_id`) REFERENCES `resort` (`resort_id`),
    CONSTRAINT `uk_resort_camera_resort_code` UNIQUE (`resort_id`, `code`),
    CONSTRAINT `chk_resort_camera_source_type` CHECK (`source_type` IN ('HLS', 'YOUTUBE', 'IFRAME', 'EXTERNAL_LINK')),
    CONSTRAINT `chk_resort_camera_display_order` CHECK (`display_order` >= 0),
    INDEX `idx_resort_camera_active_order` (`resort_id`, `is_active`, `display_order`, `resort_camera_id`)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `resort_camera` (`resort_id`, `code`, `name`, `source_type`, `source_url`, `external_page_url`, `display_order`, `is_active`)
SELECT r.resort_id, 'CAM_01', '정상 휴게소', 'HLS', 'https://video.fpkorea.com/konjiam/cam01.stream/playlist.m3u8', 'https://m.konjiamresort.co.kr/ski/skiLveCam.dev', 1, TRUE FROM resort r WHERE r.code = 'KONJIAM' UNION ALL
SELECT r.resort_id, 'CAM_02', '정상부 슬로프 (씽큐, 그램)', 'HLS', 'https://video.fpkorea.com/konjiam/cam02.stream/playlist.m3u8', 'https://m.konjiamresort.co.kr/ski/skiLveCam.dev', 2, TRUE FROM resort r WHERE r.code = 'KONJIAM' UNION ALL
SELECT r.resort_id, 'CAM_03', '초중급 베이스 (휘센)', 'HLS', 'https://video.fpkorea.com/konjiam/cam03.stream/playlist.m3u8', 'https://m.konjiamresort.co.kr/ski/skiLveCam.dev', 3, TRUE FROM resort r WHERE r.code = 'KONJIAM' UNION ALL
SELECT r.resort_id, 'CAM_04', '중상급 베이스 (와이낫, CNP)', 'HLS', 'https://video.fpkorea.com/konjiam/cam04.stream/playlist.m3u8', 'https://m.konjiamresort.co.kr/ski/skiLveCam.dev', 4, TRUE FROM resort r WHERE r.code = 'KONJIAM' UNION ALL
SELECT r.resort_id, 'CAM_05', '중간 슬로프 (와이낫 상단)', 'HLS', 'https://video.fpkorea.com/konjiam/cam05.stream/playlist.m3u8', 'https://m.konjiamresort.co.kr/ski/skiLveCam.dev', 5, TRUE FROM resort r WHERE r.code = 'KONJIAM' UNION ALL
SELECT r.resort_id, 'CAM_01', '레몬 탑승장', 'HLS', 'https://ant.livecity.co.kr:5443/jisancam/streams/jisan1.m3u8', 'https://www.jisanresort.co.kr/m/ski/slopes/webcam.asp', 1, TRUE FROM resort r WHERE r.code = 'JISAN' UNION ALL
SELECT r.resort_id, 'CAM_02', '오렌지 / 뉴오렌지 탑승장', 'HLS', 'https://ant.livecity.co.kr:5443/jisancam/streams/jisan2.m3u8', 'https://www.jisanresort.co.kr/m/ski/slopes/webcam.asp', 2, TRUE FROM resort r WHERE r.code = 'JISAN' UNION ALL
SELECT r.resort_id, 'CAM_03', '5번 / 6번 슬로프', 'HLS', 'https://ant.livecity.co.kr:5443/jisancam/streams/jisan3.m3u8', 'https://www.jisanresort.co.kr/m/ski/slopes/webcam.asp', 3, TRUE FROM resort r WHERE r.code = 'JISAN' UNION ALL
SELECT r.resort_id, 'CAM_04', '블루 탑승장', 'HLS', 'https://ant.livecity.co.kr:5443/jisancam/streams/jisan4.m3u8', 'https://www.jisanresort.co.kr/m/ski/slopes/webcam.asp', 4, TRUE FROM resort r WHERE r.code = 'JISAN' UNION ALL
SELECT r.resort_id, 'CAM_05', '실버 탑승장', 'HLS', 'https://ant.livecity.co.kr:5443/jisancam/streams/jisan5.m3u8', 'https://www.jisanresort.co.kr/m/ski/slopes/webcam.asp', 5, TRUE FROM resort r WHERE r.code = 'JISAN' UNION ALL
SELECT r.resort_id, 'CAM_01', '실시간 영상', 'YOUTUBE', 'https://www.youtube.com/embed/O6_8VBu4FSA', 'https://www.youtube.com/@11-lf8zw/streams', 1, TRUE FROM resort r WHERE r.code = 'ELYSIAN' UNION ALL
SELECT r.resort_id, 'CAM_01', '슬로프 전경', 'IFRAME', '/vivaldi.html?channel=8&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 1, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_02', '발라드 상단', 'IFRAME', '/vivaldi.html?channel=11&serial=TD0314A17496&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 2, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_03', '발라드 하단', 'IFRAME', '/vivaldi.html?channel=2&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 3, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_04', '재즈 상단', 'IFRAME', '/vivaldi.html?channel=4&serial=XU0121A37907&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 4, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_05', '재즈 하단', 'IFRAME', '/vivaldi.html?channel=1&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 5, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_06', '테크노 상단', 'IFRAME', '/vivaldi.html?channel=2&serial=XU0121A37904&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 6, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_07', '테크노 하단', 'IFRAME', '/vivaldi.html?channel=5&serial=XU0121A37904&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 7, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_08', '블루스', 'IFRAME', '/vivaldi.html?channel=4&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 8, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_09', '클래식', 'IFRAME', '/vivaldi.html?channel=10&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 9, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_10', '레게', 'IFRAME', '/vivaldi.html?channel=10&serial=TD0314A17496&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 10, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_11', '펑키 상단', 'IFRAME', '/vivaldi.html?channel=5&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 11, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_12', '펑키 하단', 'IFRAME', '/vivaldi.html?channel=8&serial=XU0121A37904&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 12, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_13', '힙합', 'IFRAME', '/vivaldi.html?channel=6&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 13, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_14', '스키월드 정상', 'IFRAME', '/vivaldi.html?channel=9&serial=TW0014A15451&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 14, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_15', '스노위랜드1', 'IFRAME', '/vivaldi.html?channel=16&serial=TD0314A17496&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 15, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_16', '스노위랜드2', 'IFRAME', '/vivaldi.html?channel=12&serial=TD0314A17496&autoplay=true', 'https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S', 16, TRUE FROM resort r WHERE r.code = 'VIVALDI' UNION ALL
SELECT r.resort_id, 'CAM_01', '베이스', 'HLS', 'https://cctv-oak9.ktcdn.co.kr/cctv/ch2.stream/chunklist.m3u8', 'https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do', 1, TRUE FROM resort r WHERE r.code = 'OAK_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_02', 'I 슬로프', 'HLS', 'https://cctv-oak9.ktcdn.co.kr/cctv/ch9.stream/chunklist.m3u8', 'https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do', 2, TRUE FROM resort r WHERE r.code = 'OAK_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_03', 'G 슬로프 하단', 'HLS', 'https://cctv-oak9.ktcdn.co.kr/cctv/ch7.stream/chunklist.m3u8', 'https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do', 3, TRUE FROM resort r WHERE r.code = 'OAK_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_04', 'F 슬로프 상단', 'HLS', 'https://cctv-oak9.ktcdn.co.kr/cctv/ch6.stream/chunklist.m3u8', 'https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do', 4, TRUE FROM resort r WHERE r.code = 'OAK_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_05', '플라워 휴게소', 'HLS', 'https://cctv-oak9.ktcdn.co.kr/cctv/ch5.stream/chunklist.m3u8', 'https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do', 5, TRUE FROM resort r WHERE r.code = 'OAK_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_01', '알파', 'HLS', 'https://live.wellihillipark.com/wellihillipark/_definst_/cam02.stream/playlist.m3u8', 'https://m.wellihillipark.com/customer/webcam', 1, TRUE FROM resort r WHERE r.code = 'WELLI_HILLI' UNION ALL
SELECT r.resort_id, 'CAM_02', '베이스', 'HLS', 'https://live.wellihillipark.com/wellihillipark/_definst_/cam03.stream/playlist.m3u8', 'https://m.wellihillipark.com/customer/webcam', 2, TRUE FROM resort r WHERE r.code = 'WELLI_HILLI' UNION ALL
SELECT r.resort_id, 'CAM_03', '슬로프 광장 / 전경', 'HLS', 'https://live.wellihillipark.com/wellihillipark/_definst_/cam04.stream/playlist.m3u8', 'https://m.wellihillipark.com/customer/webcam', 3, TRUE FROM resort r WHERE r.code = 'WELLI_HILLI' UNION ALL
SELECT r.resort_id, 'CAM_04', '정상 광장', 'HLS', 'https://live.wellihillipark.com/wellihillipark/_definst_/cam05.stream/playlist.m3u8', 'https://m.wellihillipark.com/customer/webcam', 4, TRUE FROM resort r WHERE r.code = 'WELLI_HILLI' UNION ALL
SELECT r.resort_id, 'CAM_05', '슬로프 전경', 'HLS', 'https://live.wellihillipark.com/wellihillipark/_definst_/cam06.stream/playlist.m3u8', 'https://m.wellihillipark.com/customer/webcam', 5, TRUE FROM resort r WHERE r.code = 'WELLI_HILLI' UNION ALL
SELECT r.resort_id, 'CAM_06', '워터플래닛', 'HLS', 'https://live.wellihillipark.com/wellihillipark/_definst_/cam07.stream/playlist.m3u8', 'https://m.wellihillipark.com/customer/webcam', 6, TRUE FROM resort r WHERE r.code = 'WELLI_HILLI' UNION ALL
SELECT r.resort_id, 'CAM_01', '호크 / 스패로우', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/yh_02.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 1, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_02', '도도', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/sp_01.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 2, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_03', '불새마루', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/ht_01.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 3, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_04', '베이스', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/bc_02.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 4, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_05', '펭귄', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/bc_01.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 5, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_06', '스노우 빌리지', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/yh_01.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 6, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_07', '몽블랑 정상', 'HLS', 'https://streaming.phoenixhnr.co.kr/hls/mb_03.m3u8', 'https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie', 7, TRUE FROM resort r WHERE r.code = 'PHOENIX' UNION ALL
SELECT r.resort_id, 'CAM_01', '알펜시아 리조트 라이브캠', 'YOUTUBE', 'https://www.youtube.com/embed/I3azd8f2HhM', 'https://www.alpensia.com/guide/web-cam.do', 1, TRUE FROM resort r WHERE r.code = 'ALPENSIA' UNION ALL
SELECT r.resort_id, 'CAM_01', '발왕산 氣 스카이워크', 'HLS', 'https://live.yongpyong.co.kr/cam01/index.m3u8', 'https://www.yongpyong.co.kr/kor/guide/realTimeNews/ypResortWebcam.do', 1, TRUE FROM resort r WHERE r.code = 'YONGPYONG' UNION ALL
SELECT r.resort_id, 'CAM_02', '발왕산 천년주목숲길', 'HLS', 'https://live.yongpyong.co.kr/cam02/index.m3u8', 'https://www.yongpyong.co.kr/kor/guide/realTimeNews/ypResortWebcam.do', 2, TRUE FROM resort r WHERE r.code = 'YONGPYONG' UNION ALL
SELECT r.resort_id, 'CAM_03', '옐로우 슬로프', 'HLS', 'https://live.yongpyong.co.kr/cam11/index.m3u8', 'https://www.yongpyong.co.kr/kor/guide/realTimeNews/ypResortWebcam.do', 3, TRUE FROM resort r WHERE r.code = 'YONGPYONG' UNION ALL
SELECT r.resort_id, 'CAM_04', '베이스 전경 / 레드 슬로프', 'HLS', 'https://live.yongpyong.co.kr/cam08/index.m3u8', 'https://www.yongpyong.co.kr/kor/guide/realTimeNews/ypResortWebcam.do', 4, TRUE FROM resort r WHERE r.code = 'YONGPYONG' UNION ALL
SELECT r.resort_id, 'CAM_05', '모나용평 진입로', 'HLS', 'https://live.yongpyong.co.kr/cam05/index.m3u8', 'https://www.yongpyong.co.kr/kor/guide/realTimeNews/ypResortWebcam.do', 5, TRUE FROM resort r WHERE r.code = 'YONGPYONG' UNION ALL
SELECT r.resort_id, 'CAM_01', '하이원탑 (제우스1 입구)', 'HLS', '/api/high1-stream/1/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=1', 1, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_02', '하이원탑 (헤라2 입구)', 'HLS', '/api/high1-stream/2/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=2', 2, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_03', '마운틴허브 (스노우월드 입구)', 'HLS', '/api/high1-stream/4/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=4', 3, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_04', '마운틴허브 (아폴로3 입구)', 'HLS', '/api/high1-stream/5/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=5', 4, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_05', '마운틴허브 (아테나2 입구)', 'HLS', '/api/high1-stream/6/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=6', 5, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_06', '마운틴 베이스 (아테나 리프트)', 'HLS', '/api/high1-stream/7/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=7', 6, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_07', '아테나2 하단', 'HLS', '/api/high1-stream/8/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=8', 7, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_08', '밸리허브 (헤라 리프트 입구)', 'HLS', '/api/high1-stream/11/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=11', 8, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_09', '밸리허브 (아폴로3 합류)', 'HLS', '/api/high1-stream/12/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=12', 9, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_10', '밸리허브 (제우스3)', 'HLS', '/api/high1-stream/13/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=13', 10, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_11', '제우스3 중단', 'HLS', '/api/high1-stream/14/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=14', 11, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_12', '아폴로4 중단', 'HLS', '/api/high1-stream/15/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=15', 12, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_13', '아폴로 베이스', 'HLS', '/api/high1-stream/16/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=16', 13, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_14', '제우스3 하단', 'HLS', '/api/high1-stream/17/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=17', 14, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_15', '밸리 베이스', 'HLS', '/api/high1-stream/18/playlist.m3u8', 'https://www.high1.com/webcam/pop_webcam.do?ch=18', 15, TRUE FROM resort r WHERE r.code = 'HIGH1' UNION ALL
SELECT r.resort_id, 'CAM_01', '만선하우스', 'HLS', '/api/muju-stream/01/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=01', 1, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_02', '만선봉 정상', 'HLS', '/api/muju-stream/02/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=02', 2, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_03', '하이디하우스', 'HLS', '/api/muju-stream/03/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=03', 3, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_04', '서역기행, 썬다운', 'HLS', '/api/muju-stream/04/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=04', 4, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_05', '설천하우스', 'HLS', '/api/muju-stream/05/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=05', 5, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_06', '설천 상단 슬로프', 'HLS', '/api/muju-stream/06/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=06', 6, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_07', '설천봉 정상', 'HLS', '/api/muju-stream/07/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=07', 7, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_08', '모차르트, 미뉴에트', 'HLS', '/api/muju-stream/08/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=08', 8, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_09', '폴카', 'HLS', '/api/muju-stream/09/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=09', 9, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_10', '실크로드, 미뉴에트 하단', 'HLS', '/api/muju-stream/10/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=10', 10, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_11', '커넥션', 'HLS', '/api/muju-stream/11/playlist.m3u8', 'http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=11', 11, TRUE FROM resort r WHERE r.code = 'MUJU' UNION ALL
SELECT r.resort_id, 'CAM_01', '베이직', 'IFRAME', 'https://rtsp.me/embed/2kTsKt35/', NULL, 1, TRUE FROM resort r WHERE r.code = 'EDEN_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_02', '슬로프 광장', 'IFRAME', 'https://rtsp.me/embed/ry9aTdQh', NULL, 2, TRUE FROM resort r WHERE r.code = 'EDEN_VALLEY' UNION ALL
SELECT r.resort_id, 'CAM_01', '스키하우스', 'HLS', '/api/o2-stream/cam0.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 1, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_02', '오렌지', 'HLS', '/api/o2-stream/cam1.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 2, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_03', '버금마루', 'HLS', '/api/o2-stream/cam2.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 3, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_04', '으뜸마루', 'HLS', '/api/o2-stream/cam3.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 4, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_05', '글로리2 상단', 'HLS', '/api/o2-stream/cam20.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 5, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_06', '글로리3 상단', 'HLS', '/api/o2-stream/cam21.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 6, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_07', '드림2 상단', 'HLS', '/api/o2-stream/cam22.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 7, TRUE FROM resort r WHERE r.code = 'O2_RESORT' UNION ALL
SELECT r.resort_id, 'CAM_08', '글로리3', 'HLS', '/api/o2-stream/cam23.m3u8', 'https://www.o2resort.com/ski/webcam.asp', 8, TRUE FROM resort r WHERE r.code = 'O2_RESORT';
