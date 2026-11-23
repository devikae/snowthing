package com.ikae.snowthing.domain.columnrepost;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ikae.snowthing.global.notification.DiscordNotifier;

import lombok.RequiredArgsConstructor;

/** 커밋된 게시만 칼럼 채널로 알린다. 롤백되면 AFTER_COMMIT이 불리지 않아 올라가지 않은 글을 알리지 않는다. */
@Component
@RequiredArgsConstructor
public class ColumnRepostNotificationListener {

    private final DiscordNotifier notifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReposted(ColumnRepostedEvent event) {
        notifier.column(
                "📚 휘구리 칼럼 "
                        + event.sequenceNo()
                        + "/"
                        + event.total()
                        + " 게시\n"
                        + event.title()
                        + "\n"
                        + event.link());
    }
}
