package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import com.capstone.etp.preferences.ChannelSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/** The notifications module's only door to the database. It reads and writes {@code notifications} and nothing else. */
@Mapper
public interface NotificationMapper {

    /**
     * The idempotency step. {@code event_id} is unique, so a second delivery of the same event inserts
     * nothing and this returns 0. The caller treats 0 as "already recorded" and does no work.
     */
    @Insert("""
            INSERT INTO notifications (event_id, account_id, kind, subject, body, status)
            VALUES (#{eventId}, #{accountId}, #{kind}, #{subject}, #{body}, 'QUEUED')
            ON CONFLICT (event_id) DO NOTHING""")
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("accountId") Long accountId,
                       @Param("kind") NotificationKind kind,
                       @Param("subject") String subject,
                       @Param("body") String body);

    @Select("SELECT id FROM notifications WHERE event_id = #{eventId}")
    Long findIdByEventId(@Param("eventId") UUID eventId);

    /**
     * Claims up to {@code limit} notifications that are due. The claim moves {@code next_attempt_at}
     * forward by a lease, so another instance (SKIP LOCKED) or the next tick does not pick the same row up
     * while this one is still sending; if the instance dies mid-send the lease runs out and the row is retried.
     */
    @Select("""
            UPDATE notifications
               SET next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => #{leaseSeconds})
             WHERE id IN (SELECT id
                            FROM notifications
                           WHERE status IN ('QUEUED', 'HELD')
                             AND next_attempt_at <= CURRENT_TIMESTAMP
                           ORDER BY next_attempt_at, id
                           LIMIT #{limit}
                           FOR UPDATE SKIP LOCKED)
            RETURNING id, event_id, account_id, kind, subject, body, status, status_reason,
                      resolved_channel, channel_source, attempts, created_at, sent_at, read_at""")
    List<Notification> claimDue(@Param("limit") int limit, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE notifications
               SET status = 'SENT', status_reason = #{reason,jdbcType=VARCHAR},
                   resolved_channel = #{channel}, channel_source = #{source},
                   attempts = attempts + 1, sent_at = CURRENT_TIMESTAMP
             WHERE id = #{id}""")
    int markSent(@Param("id") Long id,
                 @Param("channel") AlertChannel channel,
                 @Param("source") ChannelSource source,
                 @Param("reason") String reason);

    /** Keeps the notification for another attempt, recording why and what channel was tried. */
    @Update("""
            UPDATE notifications
               SET status = #{status}, status_reason = #{reason,jdbcType=VARCHAR},
                   resolved_channel = #{channel,jdbcType=VARCHAR}, channel_source = #{source,jdbcType=VARCHAR},
                   attempts = attempts + 1,
                   next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => #{delaySeconds})
             WHERE id = #{id}""")
    int reschedule(@Param("id") Long id,
                   @Param("status") NotificationStatus status,
                   @Param("reason") String reason,
                   @Param("channel") AlertChannel channel,
                   @Param("source") ChannelSource source,
                   @Param("delaySeconds") int delaySeconds);

    @Update("""
            UPDATE notifications
               SET status = 'FAILED', status_reason = #{reason},
                   resolved_channel = #{channel,jdbcType=VARCHAR}, channel_source = #{source,jdbcType=VARCHAR},
                   attempts = attempts + 1
             WHERE id = #{id}""")
    int markFailed(@Param("id") Long id,
                   @Param("reason") String reason,
                   @Param("channel") AlertChannel channel,
                   @Param("source") ChannelSource source);

    // ---- customer reads. Every one of them filters on account_id, taken from the verified token's account.

    @Select("""
            SELECT id, event_id, account_id, kind, subject, body, status, status_reason,
                   resolved_channel, channel_source, attempts, created_at, sent_at, read_at
              FROM notifications
             WHERE account_id = #{accountId}
               AND (#{unreadOnly} = FALSE OR read_at IS NULL)
             ORDER BY created_at DESC, id DESC
             LIMIT #{limit}""")
    List<Notification> findByAccount(@Param("accountId") Long accountId,
                                     @Param("unreadOnly") boolean unreadOnly,
                                     @Param("limit") int limit);

    @Select("""
            SELECT id, event_id, account_id, kind, subject, body, status, status_reason,
                   resolved_channel, channel_source, attempts, created_at, sent_at, read_at
              FROM notifications
             WHERE id = #{id} AND account_id = #{accountId}""")
    Notification findByIdAndAccount(@Param("id") Long id, @Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM notifications WHERE account_id = #{accountId} AND read_at IS NULL")
    int countUnread(@Param("accountId") Long accountId);

    @Update("""
            UPDATE notifications SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
             WHERE id = #{id} AND account_id = #{accountId}""")
    int markRead(@Param("id") Long id, @Param("accountId") Long accountId);
}
