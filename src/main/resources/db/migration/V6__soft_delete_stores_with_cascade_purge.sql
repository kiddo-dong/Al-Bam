-- 매장 삭제를 소프트 삭제로 바꾼다. 즉시 완전 삭제하면 근태·급여 기록까지 그 자리에서 사라지는데,
-- 실수로 지웠을 때 되돌릴 방법이 없다. deleted_at을 표시해두고, 유예기간(90일)이 지난 뒤
-- 백그라운드 작업이 진짜로 지우는 방식으로 바꾼다. 유예기간 동안은 앱에서 안 보이지만 DB에는 남아있다.
ALTER TABLE `stores` ADD COLUMN `deleted_at` DATETIME NULL;
CREATE INDEX `idx_stores_deleted_at` ON `stores` (`deleted_at`);

-- 유예기간이 끝나 실제로 매장을 지울 때, 자식 테이블에 데이터가 남아있으면 FK 제약이 막는다
-- (실제로 겪은 문제). 매번 자식 테이블을 순서대로 직접 지우는 대신, 이 관계들에는 CASCADE를 걸어
-- stores 한 행 삭제로 전체 트리가 정리되게 한다. 기존 제약에는 ON DELETE 옵션이 없어 기본값인
-- RESTRICT였으므로, 지우고 다시 만드는 것 외에 옵션을 바꿀 방법이 없다.
--
-- DROP과 ADD를 한 ALTER 문에 같이 넣으면 같은 이름으로 다시 만드는 것뿐인데도 MySQL이
-- "Duplicate foreign key constraint name"(1826)를 낸다. 그래서 문장을 반드시 나눈다.

-- stores 직접 참조
ALTER TABLE `checklist_items` DROP FOREIGN KEY `FKjrpjxvdvcrf1ho9fxfkd22kgs`;
ALTER TABLE `checklist_items` ADD CONSTRAINT `FKjrpjxvdvcrf1ho9fxfkd22kgs` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `handover_notes` DROP FOREIGN KEY `FK47fn43ihf75wwhlu4yt7cu753`;
ALTER TABLE `handover_notes` ADD CONSTRAINT `FK47fn43ihf75wwhlu4yt7cu753` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `join_requests` DROP FOREIGN KEY `FKciwrkm3hckkei8u0d6k6jwhyu`;
ALTER TABLE `join_requests` ADD CONSTRAINT `FKciwrkm3hckkei8u0d6k6jwhyu` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `manuals` DROP FOREIGN KEY `FK99nxxm238fs52q3vjflyuau5r`;
ALTER TABLE `manuals` ADD CONSTRAINT `FK99nxxm238fs52q3vjflyuau5r` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `menu_ingredients` DROP FOREIGN KEY `FK87s3nows5dekg4cjry0v6ig1b`;
ALTER TABLE `menu_ingredients` ADD CONSTRAINT `FK87s3nows5dekg4cjry0v6ig1b` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `notices` DROP FOREIGN KEY `FKj5qg3puvvc4woymnpo7hjrv6p`;
ALTER TABLE `notices` ADD CONSTRAINT `FKj5qg3puvvc4woymnpo7hjrv6p` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `shift_templates` DROP FOREIGN KEY `FKr8m7dooen1tihhufwfd794vce`;
ALTER TABLE `shift_templates` ADD CONSTRAINT `FKr8m7dooen1tihhufwfd794vce` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `store_business_hours` DROP FOREIGN KEY `FK80xirqrvvoqvaon5435d6de0q`;
ALTER TABLE `store_business_hours` ADD CONSTRAINT `FK80xirqrvvoqvaon5435d6de0q` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `store_members` DROP FOREIGN KEY `FK7t3ew28fhd34d6rtjepe9p173`;
ALTER TABLE `store_members` ADD CONSTRAINT `FK7t3ew28fhd34d6rtjepe9p173` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `store_menus` DROP FOREIGN KEY `FKtnc6cftp4bubdsl23tifuh80b`;
ALTER TABLE `store_menus` ADD CONSTRAINT `FKtnc6cftp4bubdsl23tifuh80b` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `suppliers` DROP FOREIGN KEY `FK31osod3me2lknglh15pxr5ilb`;
ALTER TABLE `suppliers` ADD CONSTRAINT `FK31osod3me2lknglh15pxr5ilb` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;
ALTER TABLE `store_onboarding_steps` DROP FOREIGN KEY `fk_store_onboarding_steps_store`;
ALTER TABLE `store_onboarding_steps` ADD CONSTRAINT `fk_store_onboarding_steps_store` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE;

-- store_members 참조 (store_members가 CASCADE로 지워질 때 같이 정리되어야 함)
ALTER TABLE `attendances` DROP FOREIGN KEY `FK5e08i625qj0jutp0jo3v92hx9`;
ALTER TABLE `attendances` ADD CONSTRAINT `FK5e08i625qj0jutp0jo3v92hx9` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `checklist_completions` DROP FOREIGN KEY `FKmeyw3nvdbfa7bk9vu22alfc10`;
ALTER TABLE `checklist_completions` ADD CONSTRAINT `FKmeyw3nvdbfa7bk9vu22alfc10` FOREIGN KEY (`checked_by`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `handover_notes` DROP FOREIGN KEY `FKjck202sjbv7y34uj3i2boay6v`;
ALTER TABLE `handover_notes` ADD CONSTRAINT `FKjck202sjbv7y34uj3i2boay6v` FOREIGN KEY (`author_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `leave_usages` DROP FOREIGN KEY `FK4j3fe2qqyki0fdp6fcrfl3x7b`;
ALTER TABLE `leave_usages` ADD CONSTRAINT `FK4j3fe2qqyki0fdp6fcrfl3x7b` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `manuals` DROP FOREIGN KEY `FKnujm24qbfsrr66bb7gggk2hp4`;
ALTER TABLE `manuals` ADD CONSTRAINT `FKnujm24qbfsrr66bb7gggk2hp4` FOREIGN KEY (`author_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `notice_reads` DROP FOREIGN KEY `FKdcce4bi277xht92c6go6vx7kf`;
ALTER TABLE `notice_reads` ADD CONSTRAINT `FKdcce4bi277xht92c6go6vx7kf` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `notices` DROP FOREIGN KEY `FKcj2l3rbol6rbtsxy3lhfe6qbk`;
ALTER TABLE `notices` ADD CONSTRAINT `FKcj2l3rbol6rbtsxy3lhfe6qbk` FOREIGN KEY (`author_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `payrolls` DROP FOREIGN KEY `FK95fwh7jvn1emk58tf5lmib2xi`;
ALTER TABLE `payrolls` ADD CONSTRAINT `FK95fwh7jvn1emk58tf5lmib2xi` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `shifts` DROP FOREIGN KEY `FK7x22dn00ui3vwijg2ma0hdvx1`;
ALTER TABLE `shifts` ADD CONSTRAINT `FK7x22dn00ui3vwijg2ma0hdvx1` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;
ALTER TABLE `store_member_available_days` DROP FOREIGN KEY `FKsaxbskktb30s1nw76cdgti99n`;
ALTER TABLE `store_member_available_days` ADD CONSTRAINT `FKsaxbskktb30s1nw76cdgti99n` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE;

-- 2단계 아래(체크리스트 항목, 매뉴얼, 메뉴, 공지, 거래처)에 딸린 테이블
ALTER TABLE `checklist_completions` DROP FOREIGN KEY `FKf5u9bh9qdt8t9sbvxd0wf8e3a`;
ALTER TABLE `checklist_completions` ADD CONSTRAINT `FKf5u9bh9qdt8t9sbvxd0wf8e3a` FOREIGN KEY (`item_id`) REFERENCES `checklist_items` (`id`) ON DELETE CASCADE;
ALTER TABLE `manual_images` DROP FOREIGN KEY `FKe09qax1u2bpgbd5rny25j627l`;
ALTER TABLE `manual_images` ADD CONSTRAINT `FKe09qax1u2bpgbd5rny25j627l` FOREIGN KEY (`manual_id`) REFERENCES `manuals` (`id`) ON DELETE CASCADE;
ALTER TABLE `menu_recipe_items` DROP FOREIGN KEY `FKa6s4u9rhsknuska4fgdqvkho5`;
ALTER TABLE `menu_recipe_items` ADD CONSTRAINT `FKa6s4u9rhsknuska4fgdqvkho5` FOREIGN KEY (`menu_id`) REFERENCES `store_menus` (`id`) ON DELETE CASCADE;
ALTER TABLE `menu_recipe_items` DROP FOREIGN KEY `FKk6kbvk6ci6mnpep2y0hmk6rn2`;
ALTER TABLE `menu_recipe_items` ADD CONSTRAINT `FKk6kbvk6ci6mnpep2y0hmk6rn2` FOREIGN KEY (`ingredient_id`) REFERENCES `menu_ingredients` (`id`) ON DELETE CASCADE;
ALTER TABLE `notice_reads` DROP FOREIGN KEY `FKh5mvl1a0vjnfm5q43krvmjy17`;
ALTER TABLE `notice_reads` ADD CONSTRAINT `FKh5mvl1a0vjnfm5q43krvmjy17` FOREIGN KEY (`notice_id`) REFERENCES `notices` (`id`) ON DELETE CASCADE;
ALTER TABLE `supplier_items` DROP FOREIGN KEY `FKm2t6dgtc9r1a39fop5375dtma`;
ALTER TABLE `supplier_items` ADD CONSTRAINT `FKm2t6dgtc9r1a39fop5375dtma` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`) ON DELETE CASCADE;

-- 3단계 (거래처 품목의 수량)
ALTER TABLE `supplier_item_quantities` DROP FOREIGN KEY `FKldsn5srvc9nab9oyh8nk54box`;
ALTER TABLE `supplier_item_quantities` ADD CONSTRAINT `FKldsn5srvc9nab9oyh8nk54box` FOREIGN KEY (`item_id`) REFERENCES `supplier_items` (`id`) ON DELETE CASCADE;
