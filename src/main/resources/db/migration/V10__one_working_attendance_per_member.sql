-- 한 멤버에게 "출근 중(WORKING)" 기록은 하나만 있을 수 있게 한다.
--
-- 출근 API는 먼저 "이미 출근 중인가"를 조회하고 없으면 저장한다. 버튼 연타나 네트워크 재시도로 요청이
-- 거의 동시에 두 번 오면 둘 다 "없음"을 보고 둘 다 저장해, 같은 시간을 두 번 일한 것으로 급여에 잡혔다.
-- 조회만으로는 막을 수 없으니 DB가 막게 한다.
--
-- MySQL에는 "status가 WORKING일 때만 유니크" 같은 조건부 인덱스가 없다. 그래서 WORKING일 때만 멤버
-- id를 담고 나머지는 NULL인 가상 컬럼을 두고, 거기에 유니크를 건다. 유니크 인덱스는 NULL을 여러 개
-- 허용하므로 퇴근한(DONE) 기록은 몇 개든 쌓인다. 가상 컬럼이라 저장 공간을 쓰지 않고, 엔티티에는
-- 매핑하지 않는다.
--
-- 이미 중복된 WORKING 행이 있으면 이 마이그레이션은 실패한다. 어느 쪽이 맞는 기록인지는 사람이
-- 판단해야 하므로 여기서 임의로 지우지 않는다. 배포 전 확인:
--   SELECT store_member_id, COUNT(*) FROM attendances WHERE status = 'WORKING'
--   GROUP BY store_member_id HAVING COUNT(*) > 1;

ALTER TABLE attendances
    ADD COLUMN working_member_id BIGINT
        GENERATED ALWAYS AS (IF(status = 'WORKING', store_member_id, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_attendances_one_working (working_member_id);
