-- Keep the public carpool list ordered by newest creation time without changing
-- its product semantics to departure-time order. The departure filter remains a
-- residual predicate when this ordering index is selected.

ALTER TABLE `carpool_detail`
    ADD INDEX `idx_carpool_detail_created_post`
        (`created_at` DESC, `post_id` DESC);
