package com.ikae.snowthing.domain.charactertest.model;

/** 화면에 보이는 직업 8개. 이름은 프론트가 코드로 찾아 보여 주고, 서버는 코드만 내보낸다. 코드는 이미지 파일 이름({코드}.png)으로도 쓴다. */
public enum CharacterJob {
    CAPTAIN("EST"),
    RESORT_INSIDER("ESF"),
    HYPE("ENT"),
    MOOD_MAKER("ENF"),
    GRINDER("IST"),
    HOT_PACK("ISF"),
    GEAR_GEEK("INT"),
    RESORT_OUTSIDER("INF");

    private final String typePrefix;

    CharacterJob(String typePrefix) {
        this.typePrefix = typePrefix;
    }

    static CharacterJob ofTypeCode(String typeCode) {
        for (CharacterJob job : values()) {
            if (typeCode.startsWith(job.typePrefix)) {
                return job;
            }
        }
        throw new IllegalArgumentException("알 수 없는 유형 코드");
    }
}
