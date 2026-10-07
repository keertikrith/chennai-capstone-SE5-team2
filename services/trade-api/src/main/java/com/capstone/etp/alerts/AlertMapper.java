package com.capstone.etp.alerts;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;


@Mapper
public interface AlertMapper {

    String COLUMNS = """
            id, account_id, symbol, direction, threshold, status, delivery_state, delivery_attempts,
            triggered_price, triggered_at, created_at""";

    
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

   
    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE status = 'ACTIVE'")
    List<PriceAlert> findAllActive();

    @Update("UPDATE price_alerts SET status = 'CANCELLED' WHERE id = #{id} AND account_id = #{accountId} AND status = 'ACTIVE'")
    int cancel(@Param("id") Long id, @Param("accountId") Long accountId);

    @Update("""
            UPDATE price_alerts
               SET symbol = #{symbol}, direction = #{direction}, threshold = #{threshold}
             WHERE id = #{id} AND account_id = #{accountId}
               AND status IN ('ACTIVE', 'CANCELLED')
               AND delivery_state = 'NONE'""")
    int updateEditable(@Param("id") Long id,
                       @Param("accountId") Long accountId,
                       @Param("symbol") String symbol,
                       @Param("direction") AlertDirection direction,
                       @Param("threshold") BigDecimal threshold);

    @Update("""
            UPDATE price_alerts
               SET status = 'ACTIVE'
             WHERE id = #{id} AND account_id = #{accountId}
               AND status = 'CANCELLED'
               AND delivery_state = 'NONE'""")
    int enable(@Param("id") Long id, @Param("accountId") Long accountId);

    @Update("""
            UPDATE price_alerts
               SET status = 'CANCELLED'
             WHERE id = #{id} AND account_id = #{accountId}
               AND status = 'ACTIVE'
               AND delivery_state = 'NONE'""")
    int disable(@Param("id") Long id, @Param("accountId") Long accountId);

    @Delete("""
            DELETE FROM price_alerts
             WHERE id = #{id} AND account_id = #{accountId}
               AND status IN ('TRIGGERED', 'CANCELLED')
               AND delivery_state NOT IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')""")
    int deleteFinished(@Param("id") Long id, @Param("accountId") Long accountId);

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

   
    @Select("SELECT " + COLUMNS + " FROM price_alerts " + """
             WHERE status = 'TRIGGERED'
               AND delivery_state IN ('PENDING_HANDOVER', 'HANDOVER_FAILED')
               AND delivery_attempts < #{maxAttempts}
               AND triggered_at < CURRENT_TIMESTAMP - make_interval(secs => 10)
             ORDER BY triggered_at
             LIMIT #{limit}""")
    List<PriceAlert> findPendingHandover(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);
}
