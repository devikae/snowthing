-- ====================================================================
-- Snowthing Production Migration
-- Version: 006
-- Description: 휘닉스파크 HLS 스트림 최신화 및 프론트엔드 스트림 프록시 경로(/stream-proxy/) 전환
-- ====================================================================

-- 1. 휘닉스파크 6개소 최신 실시간 HLS 스트림 URL 갱신
UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/yh_02_02.m3u8'
WHERE r.code = 'PHOENIX' AND rc.code = 'CAM_01';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/sp_01_01.m3u8'
WHERE r.code = 'PHOENIX' AND rc.code = 'CAM_02';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/ht_01_01.m3u8'
WHERE r.code = 'PHOENIX' AND rc.code = 'CAM_03';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/bc_02_01.m3u8'
WHERE r.code = 'PHOENIX' AND rc.code = 'CAM_04';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/yh_01_01.m3u8'
WHERE r.code = 'PHOENIX' AND rc.code = 'CAM_06';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = 'https://streaming.phoenixhnr.co.kr/hls/mb_03_02.m3u8'
WHERE r.code = 'PHOENIX' AND rc.code = 'CAM_07';

-- 2. 하이원, 오투, 무주 스트림 프록시 경로 변경 (/api/* -> /stream-proxy/*)
UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = REPLACE(rc.source_url, '/api/high1-stream/', '/stream-proxy/high1/')
WHERE r.code = 'HIGH1';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = REPLACE(rc.source_url, '/api/o2-stream/', '/stream-proxy/o2/')
WHERE r.code = 'O2_RESORT';

UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = REPLACE(rc.source_url, '/api/muju-stream/', '/stream-proxy/muju/')
WHERE r.code = 'MUJU';

-- 3. 비발디파크 CAM_09 (클래식) 정품 시리얼 번호 보정
UPDATE `resort_camera` rc
JOIN `resort` r ON rc.resort_id = r.resort_id
SET rc.source_url = '/vivaldi.html?channel=10&serial=TW0014A15451&autoplay=true'
WHERE r.code = 'VIVALDI' AND rc.code = 'CAM_09';
