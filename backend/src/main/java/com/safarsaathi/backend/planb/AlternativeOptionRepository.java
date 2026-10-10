package com.safarsaathi.backend.planb;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and saves {@link AlternativeOption} rows (Spring Data writes the code). */
public interface AlternativeOptionRepository extends JpaRepository<AlternativeOption, Long> {

    /** The plan's options: ranked ones best first, then the rejected ones (rank 0), each in id order. */
    @Query("select o from AlternativeOption o where o.planId = :planId "
            + "order by o.rejected asc, o.rankOrder asc, o.id asc")
    List<AlternativeOption> findForPlan(@Param("planId") long planId);

    /** Deletes the plan's options in one statement (a rebuild replaces them all). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from AlternativeOption o where o.planId = :planId")
    void deleteForPlan(@Param("planId") long planId);
}
