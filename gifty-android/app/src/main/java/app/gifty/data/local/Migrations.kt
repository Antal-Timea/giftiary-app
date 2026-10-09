package app.gifty.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v2 gives pending sync operations an owner, so pending work can be read per account.
 *
 * Existing operations keep every value and get a null owner. A v1 database belongs to a single
 * legacy device owner, so a null owner means exactly "created under v1, not yet claimed"; the
 * legacy ownership claim assigns it later. The owner is deliberately not guessed here.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_operation` ADD COLUMN `ownerUserId` TEXT")
        db.execSQL("DROP INDEX IF EXISTS `index_sync_operation_createdAt`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_operation_ownerUserId_createdAt` " +
                "ON `sync_operation` (`ownerUserId`, `createdAt`)",
        )
    }
}

/**
 * v3 gives every pending sync operation a mutation id, and adds the conflict table sync will use.
 *
 * Each existing operation is a pending change the backend has never seen, so each gets its own new
 * random mutation id. SQLite cannot generate those, and a NOT NULL column needs a value for every
 * existing row, so the table is rebuilt and each row is copied with an id generated here. Nothing
 * was ever sent, so nothing is in flight or blocked. Every other value is kept, including a null
 * owner still waiting for the legacy claim.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `sync_operation_new` (`id` TEXT NOT NULL, `ownerUserId` TEXT, `entityType` TEXT NOT NULL, " +
                "`entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `mutationId` TEXT NOT NULL, " +
                "`inFlightMutationId` TEXT, `blockedReason` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`attemptCount` INTEGER NOT NULL, `lastAttemptAt` INTEGER, `lastErrorCode` TEXT, PRIMARY KEY(`id`))",
        )
        val operationIds = db.query("SELECT `id` FROM `sync_operation`").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        operationIds.forEach { operationId ->
            db.execSQL(
                "INSERT INTO `sync_operation_new` (`id`, `ownerUserId`, `entityType`, `entityId`, `operation`, " +
                    "`mutationId`, `inFlightMutationId`, `blockedReason`, `createdAt`, `attemptCount`, " +
                    "`lastAttemptAt`, `lastErrorCode`) " +
                    "SELECT `id`, `ownerUserId`, `entityType`, `entityId`, `operation`, ?, NULL, NULL, `createdAt`, " +
                    "`attemptCount`, `lastAttemptAt`, `lastErrorCode` FROM `sync_operation` WHERE `id` = ?",
                arrayOf<Any?>(UUID.randomUUID().toString(), operationId),
            )
        }
        db.execSQL("DROP TABLE `sync_operation`")
        db.execSQL("ALTER TABLE `sync_operation_new` RENAME TO `sync_operation`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_operation_entityType_entityId` " +
                "ON `sync_operation` (`entityType`, `entityId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_operation_ownerUserId_createdAt` " +
                "ON `sync_operation` (`ownerUserId`, `createdAt`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_conflict` (`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, " +
                "`entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `mutationId` TEXT NOT NULL, " +
                "`baseVersion` INTEGER NOT NULL, `remoteVersion` INTEGER NOT NULL, `localSnapshotJson` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `seenAt` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_conflict_ownerUserId_seenAt` " +
                "ON `sync_conflict` (`ownerUserId`, `seenAt`)",
        )
    }
}
