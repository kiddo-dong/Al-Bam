-- 매장이 멤버를 부르는 직함. "주방장", "홀팀장"처럼 매장마다 다르게 쓴다.
--
-- role(OWNER/MANAGER/STAFF)과는 별개다. role은 무엇을 할 수 있는지를 정하고, 이 값은 화면에 보이는
-- 이름일 뿐이다. 하나로 합치면 직함을 바꾸다 권한이 함께 바뀌어, 이름을 고쳤다가 급여 정보가 열리는
-- 일이 생긴다.
--
-- 안 정한 멤버는 NULL이고, 그때는 화면이 역할 이름을 그대로 보여주면 된다.

ALTER TABLE store_members ADD COLUMN title VARCHAR(20) NULL;
