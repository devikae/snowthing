package com.ikae.snowthing.domain.resortweather.source;

/** 위경도를 기상청 동네예보 격자(5km)로 바꾼다. 기상청 단기예보 조회서비스 활용가이드의 Lambert Conformal Conic 변환식을 그대로 옮겼다. */
public record KmaGrid(int nx, int ny) {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double STANDARD_LAT1 = 30.0;
    private static final double STANDARD_LAT2 = 60.0;
    private static final double ORIGIN_LON = 126.0;
    private static final double ORIGIN_LAT = 38.0;
    private static final double ORIGIN_X = 43;
    private static final double ORIGIN_Y = 136;

    public static KmaGrid fromLatLon(double latitude, double longitude) {
        double degrad = Math.PI / 180.0;
        double re = EARTH_RADIUS_KM / GRID_KM;
        double slat1 = STANDARD_LAT1 * degrad;
        double slat2 = STANDARD_LAT2 * degrad;
        double olon = ORIGIN_LON * degrad;
        double olat = ORIGIN_LAT * degrad;

        double sn =
                Math.log(Math.cos(slat1) / Math.cos(slat2))
                        / Math.log(
                                Math.tan(Math.PI * 0.25 + slat2 * 0.5)
                                        / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);

        double ra = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + latitude * degrad * 0.5), sn);
        double theta = longitude * degrad - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;
        int nx = (int) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + ORIGIN_Y + 0.5);
        return new KmaGrid(nx, ny);
    }
}
