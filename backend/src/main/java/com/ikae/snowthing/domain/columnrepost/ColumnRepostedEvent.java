package com.ikae.snowthing.domain.columnrepost;

/** 칼럼 하나가 게시됐다. 커밋된 뒤에만 디스코드로 알린다. */
public record ColumnRepostedEvent(int sequenceNo, long total, String title, String link) {}
