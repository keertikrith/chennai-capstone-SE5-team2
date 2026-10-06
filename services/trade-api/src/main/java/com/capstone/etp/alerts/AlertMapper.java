package com.capstone.etp.alerts;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/** The alerts module's only door to the database. It reads and writes {@code price_alerts} and nothing else. */
@Mapper
public interface AlertMapper {

    String COLUMNS = """
            id, account_id, symbol, direction, threshold, status, delivery_state, delivery_attempts,
            triggered_price, triggered_at, created_at""";

    /**
     * Creates an alert only if the account holds fewer than {@code cap}. The count and the insert are
     * ONE statement, so two simultaneous requests cannot both slip under the cap. Returns the new id, or
     * null when the cap is reached.
     */
    @Select("""
            INSERT INTO price_alerts (account_id, symbol, direction, threshold)
            SELECT #{accountId}, #{symbol}, #{direction}, #{threshold}
             WHERE (SELECT COUNT(*) FROM price_alerts WHERE account_id = #{accountId}) < #{cap}
            RETURNING id""")
    Long insertWithinCap(@Param("accountId") Long accountId,
                         @Param("symbol") String symbol,
                         @Param("direction") AlertDirection direction,
                         @Param("threshold") BigDecimal threshold,
                         @Param("cap") int cap);

    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE account_id = #{accountId} ORDER BY created_at DESC, id DESC")
    List<PriceAlert> findByAccount(@Param("accountId") Long accountId);

    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE id = #{id} AND account_id = #{accountId}")
    PriceAlert findByIdAndAccount(@Param("id") Long id, @Param("accountId") Long accountId);

    /** Every active alert, to build the per-symbol index the quote consumer looks in. */
    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE status = 'ACTIVE'")
    List<PriceAlert> findAllActive();

    @Update("UPDATE price_alerts SET status = 'CANCELLED' WHERE id = #{id} AND account_id = #{accountId} AND status = 'ACTIVE'")
    int cancel(@Param("id") Long id, @Param("accountId") Long accountId);

    /** Removes a finished alert, but never one whose hand-over to notifications is still unresolved. */
    @Delete("""
            DELETE FROM price_alerts
             WHERE id = #{id} AND account_id = #{accountId}
               AND status IN ('TRIGGERED', 'CANCELLED')
               AND delivery_state NOT IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')""")
    int deleteFinished(@Param("id") Long id, @Param("accountId") Long accountId);

    /**
     * The guarded transition: only an ACTIVE alert can fire, and only once. Zero rows affected means
     * another consumer, a redelivered quote or a cancel got there first, and the caller does nothing.
     */
    @Update("""
            UPDATE price_alerts
               SET status = 'TRIGGERED', delivery_state = 'PENDING_HANDOVER',
                   triggered_price = #{price}, triggered_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'ACTIVE'""")
    int markTriggered(@Param("id") Long id, @Param("price") BigDecimal price);

    @Update("""
            UPDATE price_alerts
               SET delivery_state = 'HANDED_OVER', delivery_attempts = delivery_attempts + 1
             WHERE id = #{id} AND status = 'TRIGGERED'""")
    int markHandedOver(@Param("id") Long id);

    @Update("""
            UPDATE price_alerts
               SET delivery_state = 'HANDOVER_FAILED', delivery_attempts = delivery_attempts + 1
             WHERE id = #{id} AND status = 'TRIGGERED'""")
    int markHandoverFailed(@Param("id") Long id);

    /** Fired alerts whose hand-over is not confirmed, left a moment so the normal path is not raced. */
    // The FROM sits in the single-line string: a text block would strip its leading space and glue it to the last column.
    @Select("SELECT " + COLUMNS + " FROM price_alerts " + """
             WHERE status = 'TRIGGERED'
               AND delivery_state IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')
               AND delivery_attempts < #{maxAttempts}
               AND triggered_at < CURRENT_TIMESTAMP - make_interval(secs => 10)
             ORDER BY triggered_at
             LIMIT #{limit}""")
    List<PriceAlert> findPendingHandover(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);
}
