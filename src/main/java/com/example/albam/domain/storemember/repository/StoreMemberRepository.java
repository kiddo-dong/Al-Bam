package com.example.albam.domain.storemember.repository;

import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {

    /**
     * 매장 권한 검사의 관문.
     *
     * <p>파생 쿼리로 두면 store_id 컬럼만 보기 때문에, 소프트 삭제된 매장의 멤버도 그대로 찾아진다.
     * 그러면 매장은 사라졌는데 권한 검사는 통과해, 지운 매장에 공지나 체크리스트가 계속 등록된다.
     * store를 명시적으로 조인하면 Store에 걸린 @SQLRestriction이 적용되어 그런 행이 걸러진다.
     */
    @Query("select m from StoreMember m join m.store s where s.id = :storeId and m.user.id = :userId")
    Optional<StoreMember> findByStoreIdAndUserId(@Param("storeId") Long storeId,
            @Param("userId") Long userId);

    /**
     * 멤버 목록은 거의 항상 이름을 함께 보여준다(멤버 관리, 공지 읽음 현황, 주간 근무 현황, AI 초안).
     * 그래서 user를 함께 읽는다 — 안 그러면 멤버 수만큼 조회가 따라붙는다.
     */
    @EntityGraph(attributePaths = {"user"})
    List<StoreMember> findAllByStoreId(Long storeId);

    /**
     * 내 매장 목록. 항목마다 매장 정보를 보여주므로 store를 함께 읽는다.
     *
     * <p>매장의 영업시간(@ElementCollection)도 응답에 들어가지만 여기서 조인하지 않는다. 컬렉션을
     * 조인하면 매장 하나가 요일 수만큼 행으로 불어나 결과가 중복된다. 그쪽은
     * default_batch_fetch_size가 IN 한 번으로 모아 읽는다.
     */
    @EntityGraph(attributePaths = {"store"})
    List<StoreMember> findAllByUserIdAndStatus(Long userId, MemberStatus status);

    boolean existsByStoreIdAndUserIdAndStatus(Long storeId, Long userId, MemberStatus status);

    boolean existsByUserIdAndStatus(Long userId, MemberStatus status);
}
