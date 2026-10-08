package com.capstone.etp.mcp;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads and writes mcp_connectors, mcp_pending_orders and mcp_audit_log. Nothing else. */
@Mapper
public interface McpMapper {

    String CONNECTOR_COLUMNS = """
            id, account_id, label, token_hash, scopes, max_order_value, max_orders_per_day,
            created_at, expires_at, last_used_at, revoked_at""";

    String PENDING_COLUMNS = """
            id, connector_id, account_id, symbol, side, quantity, price, est_value, status, order_id, detail,
            created_at, expires_at, decided_at""";

    @Insert("""
            INSERT INTO mcp_connectors (id, account_id, label, token_hash, scopes, max_order_value,
                                        max_orders_per_day, expires_at)
            SELECT #{id}, #{accountId}, #{label}, #{tokenHash}, #{scopes}, #{maxOrderValue},
                   #{maxOrdersPerDay}, #{expiresAt}
             WHERE (SELECT COUNT(*) FROM mcp_connectors
                     WHERE account_id = #{accountId} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP) < #{cap}""")
    int insertConnectorWithinCap(@Param("id") UUID id,
                                 @Param("accountId") Long accountId,
                                 @Param("label") String label,
                                 @Param("tokenHash") String tokenHash,
                                 @Param("scopes") String scopes,
                                 @Param("maxOrderValue") BigDecimal maxOrderValue,
                                 @Param("maxOrdersPerDay") int maxOrdersPerDay,
                                 @Param("expiresAt") Instant expiresAt,
                                 @Param("cap") int cap);

    @Select("SELECT " + CONNECTOR_COLUMNS + " FROM mcp_connectors WHERE account_id = #{accountId} ORDER BY created_at DESC")
    List<McpConnector> findConnectorsByAccount(@Param("accountId") Long accountId);

    @Select("SELECT " + CONNECTOR_COLUMNS + " FROM mcp_connectors WHERE token_hash = #{tokenHash}")
    McpConnector findConnectorByHash(@Param("tokenHash") String tokenHash);

    /** The connector, but only while it is neither revoked nor expired. */
    @Select("SELECT " + CONNECTOR_COLUMNS + """
             FROM mcp_connectors
            WHERE id = #{id} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP""")
    McpConnector findActiveConnector(@Param("id") UUID id);

    @Update("UPDATE mcp_connectors SET revoked_at = CURRENT_TIMESTAMP WHERE id = #{id} AND account_id = #{accountId} AND revoked_at IS NULL")
    int revokeConnector(@Param("id") UUID id, @Param("accountId") Long accountId);

    @Update("""
            UPDATE mcp_connectors SET last_used_at = CURRENT_TIMESTAMP
             WHERE id = #{id} AND (last_used_at IS NULL OR last_used_at < CURRENT_TIMESTAMP - INTERVAL '1 minute')""")
    int touchConnector(@Param("id") UUID id);

    @Insert("""
            INSERT INTO mcp_pending_orders (id, connector_id, account_id, symbol, side, quantity, price, est_value,
                                            status, expires_at)
            VALUES (#{id}, #{connectorId}, #{accountId}, #{symbol}, #{side}, #{quantity}, #{price}, #{estValue},
                    'PENDING', #{expiresAt})""")
    int insertPending(@Param("id") UUID id,
                      @Param("connectorId") UUID connectorId,
                      @Param("accountId") Long accountId,
                      @Param("symbol") String symbol,
                      @Param("side") String side,
                      @Param("quantity") int quantity,
                      @Param("price") BigDecimal price,
                      @Param("estValue") BigDecimal estValue,
                      @Param("expiresAt") Instant expiresAt);

    @Select("SELECT " + PENDING_COLUMNS + " FROM mcp_pending_orders WHERE id = #{id} AND connector_id = #{connectorId}")
    McpPendingOrder findPending(@Param("id") UUID id, @Param("connectorId") UUID connectorId);

    /** One order wins this: only a PENDING, unexpired row of this connector moves to SUBMITTING. */
    @Update("""
            UPDATE mcp_pending_orders SET status = 'SUBMITTING'
             WHERE id = #{id} AND connector_id = #{connectorId}
               AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP""")
    int claimPending(@Param("id") UUID id, @Param("connectorId") UUID connectorId);

    @Update("""
            UPDATE mcp_pending_orders
               SET status = #{status}, order_id = #{orderId,jdbcType=OTHER}, detail = #{detail,jdbcType=VARCHAR},
                   decided_at = CURRENT_TIMESTAMP
             WHERE id = #{id}""")
    int settlePending(@Param("id") UUID id,
                      @Param("status") String status,
                      @Param("orderId") UUID orderId,
                      @Param("detail") String detail);

    /** Prepared orders still waiting to be confirmed. */
    @Select("""
            SELECT COUNT(*) FROM mcp_pending_orders
             WHERE connector_id = #{connectorId} AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP""")
    int countWaiting(@Param("connectorId") UUID connectorId);

    /** Orders this connector placed (or is placing) today. */
    @Select("""
            SELECT COUNT(*) FROM mcp_pending_orders
             WHERE connector_id = #{connectorId} AND status IN ('SUBMITTING', 'PLACED')
               AND created_at >= date_trunc('day', CURRENT_TIMESTAMP)""")
    int countPlacedToday(@Param("connectorId") UUID connectorId);

    @Insert("""
            INSERT INTO mcp_audit_log (connector_id, account_id, tool, outcome, detail)
            VALUES (#{connectorId,jdbcType=OTHER}, #{accountId,jdbcType=BIGINT}, #{tool}, #{outcome}, #{detail,jdbcType=VARCHAR})""")
    int insertAudit(@Param("connectorId") UUID connectorId,
                    @Param("accountId") Long accountId,
                    @Param("tool") String tool,
                    @Param("outcome") String outcome,
                    @Param("detail") String detail);

    @Select("""
            SELECT a.id, a.connector_id, c.label AS connector_label, a.tool, a.outcome, a.detail, a.created_at
              FROM mcp_audit_log a LEFT JOIN mcp_connectors c ON c.id = a.connector_id
             WHERE a.account_id = #{accountId}
             ORDER BY a.created_at DESC, a.id DESC
             LIMIT #{limit}""")
    List<Map<String, Object>> recentAudit(@Param("accountId") Long accountId, @Param("limit") int limit);
}
