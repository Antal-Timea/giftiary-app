package app.gifty.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
