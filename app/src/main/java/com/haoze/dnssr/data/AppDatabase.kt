package com.haoze.dnssr.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.haoze.dnssr.data.dao.AllowRuleDao
import com.haoze.dnssr.data.dao.BlockRuleDao
import com.haoze.dnssr.data.dao.BootstrapLogDao
import com.haoze.dnssr.data.dao.DnsCacheDao
import com.haoze.dnssr.data.dao.DnsLogDao
import com.haoze.dnssr.data.dao.RaceLogDao
import com.haoze.dnssr.data.dao.SubscriptionDao
import com.haoze.dnssr.data.entity.AllowRuleEntity
import com.haoze.dnssr.data.entity.AllowRuleSourceEntity
import com.haoze.dnssr.data.entity.BlockRuleEntity
import com.haoze.dnssr.data.entity.BlockRuleSourceEntity
import com.haoze.dnssr.data.entity.BootstrapLogEntity
import com.haoze.dnssr.data.entity.DnsCacheEntity
import com.haoze.dnssr.data.entity.DnsLogEntity
import com.haoze.dnssr.data.entity.RaceLogEntity
import com.haoze.dnssr.data.entity.SubscriptionEntity

@Database(
    entities = [
        DnsCacheEntity::class,
        DnsLogEntity::class,
        RaceLogEntity::class,
        BootstrapLogEntity::class,
        BlockRuleEntity::class,
        BlockRuleSourceEntity::class,
        AllowRuleEntity::class,
        AllowRuleSourceEntity::class,
        SubscriptionEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dnsCacheDao(): DnsCacheDao
    abstract fun dnsLogDao(): DnsLogDao
    abstract fun raceLogDao(): RaceLogDao
    abstract fun bootstrapLogDao(): BootstrapLogDao
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun allowRuleDao(): AllowRuleDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dnssr_database"
                )
                    .addMigrations(
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `allow_rule` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `rawLine` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `groupName` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_allow_rule_pattern` ON `allow_rule` (`pattern`)")
                db.execSQL("ALTER TABLE `subscription` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'block'")
                db.execSQL("DROP INDEX IF EXISTS `index_subscription_url`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subscription_url_kind` ON `subscription` (`url`, `kind`)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dns_log` ADD COLUMN `blockSubscriptionId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dns_log_block_subscription_timestamp` " +
                        "ON `dns_log` (`blockSubscriptionId`, `timestamp`)"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subscription` ADD COLUMN `sourceType` TEXT NOT NULL DEFAULT 'remote'"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TEMP TABLE subscription_merge AS " +
                        "SELECT s.id AS oldId, (SELECT MIN(k.id) FROM subscription k " +
                        "WHERE LOWER(k.url) = LOWER(s.url)) AS keepId FROM subscription s"
                )
                db.execSQL(
                    "UPDATE block_rule SET source = 'sub_' || " +
                        "(SELECT keepId FROM subscription_merge WHERE oldId = " +
                        "CAST(SUBSTR(block_rule.source, 5) AS INTEGER)) " +
                        "WHERE source LIKE 'sub_%' AND EXISTS " +
                        "(SELECT 1 FROM subscription_merge WHERE oldId = CAST(SUBSTR(block_rule.source, 5) AS INTEGER))"
                )
                db.execSQL(
                    "UPDATE allow_rule SET source = 'sub_' || " +
                        "(SELECT keepId FROM subscription_merge WHERE oldId = " +
                        "CAST(SUBSTR(allow_rule.source, 5) AS INTEGER)) " +
                        "WHERE source LIKE 'sub_%' AND EXISTS " +
                        "(SELECT 1 FROM subscription_merge WHERE oldId = CAST(SUBSTR(allow_rule.source, 5) AS INTEGER))"
                )
                db.execSQL("DELETE FROM subscription WHERE id NOT IN (SELECT keepId FROM subscription_merge)")
                db.execSQL("UPDATE subscription SET kind = 'block'")
                db.execSQL("DROP TABLE subscription_merge")

                db.execSQL("DROP INDEX IF EXISTS index_block_rule_pattern")
                db.execSQL("DROP INDEX IF EXISTS index_allow_rule_pattern")
                db.execSQL("DROP INDEX IF EXISTS index_subscription_url_kind")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_block_rule_pattern_source " +
                        "ON block_rule (pattern, source)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_allow_rule_pattern_source " +
                        "ON allow_rule (pattern, source)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_subscription_url ON subscription (url)"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subscription` ADD COLUMN `importState` TEXT NOT NULL DEFAULT 'ready'"
                )
                db.execSQL("ALTER TABLE `subscription` ADD COLUMN `importError` TEXT")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateRuleTable(db, "block_rule")
                migrateRuleTable(db, "allow_rule")
            }

            private fun migrateRuleTable(db: SupportSQLiteDatabase, table: String) {
                val newTable = "${table}_new"
                val sourceData = "${table}_source_data"
                val sourceTable = "${table}_source"

                db.execSQL(
                    "CREATE TABLE `$newTable` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pattern` TEXT NOT NULL, `rawLine` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `groupName` TEXT)"
                )
                db.execSQL(
                    "INSERT INTO `$newTable` (`id`, `pattern`, `rawLine`, `addedAt`, `enabled`, `groupName`) " +
                        "SELECT r.id, r.pattern, r.rawLine, r.addedAt, " +
                        "(SELECT MAX(e.enabled) FROM `$table` e WHERE e.pattern = r.pattern), r.groupName " +
                        "FROM `$table` r WHERE r.id = " +
                        "(SELECT MIN(k.id) FROM `$table` k WHERE k.pattern = r.pattern)"
                )
                db.execSQL(
                    "CREATE TEMP TABLE `$sourceData` AS " +
                        "SELECT n.id AS ruleId, o.source AS source, MAX(o.enabled) AS enabled " +
                        "FROM `$table` o JOIN `$newTable` n ON n.pattern = o.pattern " +
                        "GROUP BY n.id, o.source"
                )
                db.execSQL("DROP TABLE `$table`")
                db.execSQL("ALTER TABLE `$newTable` RENAME TO `$table`")
                db.execSQL("CREATE UNIQUE INDEX `index_${table}_pattern` ON `$table` (`pattern`)")
                db.execSQL(
                    "CREATE TABLE `$sourceTable` (" +
                        "`ruleId` INTEGER NOT NULL, `source` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ruleId`, `source`), " +
                        "FOREIGN KEY(`ruleId`) REFERENCES `$table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `$sourceTable` (`ruleId`, `source`, `enabled`) " +
                        "SELECT ruleId, source, enabled FROM `$sourceData`"
                )
                db.execSQL("CREATE INDEX `index_${sourceTable}_source` ON `$sourceTable` (`source`)")
                db.execSQL("DROP TABLE `$sourceData`")
            }
        }
    }
}
