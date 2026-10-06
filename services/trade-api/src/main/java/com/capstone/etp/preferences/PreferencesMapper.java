package com.capstone.etp.preferences;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * The preferences module's only door to the database. It reads the customer's own profile row
 * (clients) so the contact detail stays in the one place that owns it, and it writes only
 * customer_preferences.
 */
@Mapper
public interface PreferencesMapper {

    /** The account's client, with the stored preference if there is one. Null when the account does not exist. */
    @Select("""
            SELECT c.id                 AS client_id,
                   c.email              AS email,
                   c.phone              AS phone,
                   p.alert_channel      AS alert_channel,
                   p.default_account_id AS default_account_id,
                   p.updated_at         AS updated_at
            FROM accounts a
            JOIN clients c ON c.id = a.client_id
            LEFT JOIN customer_preferences p ON p.client_id = c.id
            WHERE a.id = #{accountId}""")
    PreferenceRow findByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM accounts WHERE id = #{accountId} AND client_id = #{clientId}")
    int countAccountOfClient(@Param("accountId") Long accountId, @Param("clientId") Long clientId);

    @Insert("""
            INSERT INTO customer_preferences (client_id, default_account_id, alert_channel, updated_at)
            VALUES (#{clientId}, #{defaultAccountId}, #{alertChannel}, CURRENT_TIMESTAMP)
            ON CONFLICT (client_id) DO UPDATE
               SET default_account_id = EXCLUDED.default_account_id,
                   alert_channel      = EXCLUDED.alert_channel,
                   updated_at         = CURRENT_TIMESTAMP""")
    int upsert(@Param("clientId") Long clientId,
               @Param("defaultAccountId") Long defaultAccountId,
               @Param("alertChannel") AlertChannel alertChannel);
}
