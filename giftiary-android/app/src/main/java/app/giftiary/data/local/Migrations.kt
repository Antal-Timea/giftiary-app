package app.giftiary.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * v2 gives pending sync operations an owner, so pending work can be read per account.
 *
 * Existing operations keep every value and get a null owner. A v1 database belongs to a single
 * legacy device owner, so a null owner means exactly "created under v1". The owner is
 * deliberately not guessed here.
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
 * owner.
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

/**
 * v4 makes every foreign key immediate, so a write referring to a row that does not exist fails at
 * once and rolls back, instead of failing only at commit, which on Android leaves the transaction
 * open. Nothing else changes: each table that has foreign keys is rebuilt with its v4 definition,
 * every row is copied as it is, and its indexes are created again.
 *
 * Fails, leaving v3 untouched, if the existing rows already refer to rows that do not exist.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Room migrates before it turns foreign keys on. They must be off: dropping a referenced
        // table with them on would leave violations that renaming its replacement does not clear.
        // The result is checked as a whole below instead.
        val enforced = db.query("PRAGMA foreign_keys").use { it.moveToFirst() && it.getInt(0) != 0 }
        check(!enforced) { "Foreign keys must not be enforced while tables are rebuilt" }

        IMMEDIATE_FOREIGN_KEY_TABLES.forEach { it.rebuild(db) }

        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) error("A row of `${cursor.getString(0)}` refers to a row that does not exist")
        }
    }
}

/**
 * v5 makes wishlists and wishes same-owner throughout: a wishlist's person must be its owner's,
 * over (personId, ownerUserId), and every wish gets an owner, always its wishlist's, over
 * (wishlistId, ownerUserId). People and wishlists gain the unique (id, ownerUserId) keys these
 * foreign keys need. Every row keeps every value; each wish takes its owner from its wishlist.
 *
 * Fails, leaving v4 untouched, if a wish has no wishlist or a wishlist's person is missing or
 * another owner's. Nothing is dropped or given another owner to make old data fit.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // As in MIGRATION_3_4: the table is rebuilt, so keys are checked as a whole at the end.
        val enforced = db.query("PRAGMA foreign_keys").use { it.moveToFirst() && it.getInt(0) != 0 }
        check(!enforced) { "Foreign keys must not be enforced while tables are rebuilt" }

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_person_id_ownerUserId` ON `person` (`id`, `ownerUserId`)")

        // Room's v5 definitions, copied verbatim. Wishlist columns are unchanged.
        db.execSQL(
            "CREATE TABLE `wishlist_new` (`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `personId` TEXT, " +
                "`name` TEXT NOT NULL, `version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`personId`, `ownerUserId`) REFERENCES `person`(`id`, `ownerUserId`) " +
                "ON UPDATE CASCADE ON DELETE NO ACTION )",
        )
        val wishlistColumns = WISHLIST_COLUMNS.joinToString { "`$it`" }
        db.execSQL("INSERT INTO `wishlist_new` ($wishlistColumns) SELECT $wishlistColumns FROM `wishlist`")
        db.execSQL("DROP TABLE `wishlist`")
        db.execSQL("ALTER TABLE `wishlist_new` RENAME TO `wishlist`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishlist_ownerUserId` ON `wishlist` (`ownerUserId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishlist_personId_ownerUserId` ON `wishlist` (`personId`, `ownerUserId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wishlist_id_ownerUserId` ON `wishlist` (`id`, `ownerUserId`)")

        db.execSQL(
            "CREATE TABLE `wish_new` (`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `wishlistId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `note` TEXT, `estimatedPriceAmountMinor` INTEGER, `estimatedPriceCurrencyCode` TEXT, " +
                "`productUrl` TEXT, `photoPath` TEXT, `version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`wishlistId`, `ownerUserId`) REFERENCES `wishlist`(`id`, `ownerUserId`) " +
                "ON UPDATE CASCADE ON DELETE NO ACTION )",
        )
        val columns = WISH_V4_COLUMNS.joinToString { "`$it`" }
        db.execSQL(
            "INSERT INTO `wish_new` ($columns, `ownerUserId`) " +
                "SELECT ${WISH_V4_COLUMNS.joinToString { "w.`$it`" }}, l.`ownerUserId` " +
                "FROM `wish` w JOIN `wishlist` l ON l.`id` = w.`wishlistId`",
        )
        val before = db.query("SELECT COUNT(*) FROM `wish`").use { it.moveToFirst(); it.getInt(0) }
        val after = db.query("SELECT COUNT(*) FROM `wish_new`").use { it.moveToFirst(); it.getInt(0) }
        check(before == after) { "${before - after} wishes have no wishlist to take their owner from" }
        db.execSQL("DROP TABLE `wish`")
        db.execSQL("ALTER TABLE `wish_new` RENAME TO `wish`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wish_ownerUserId` ON `wish` (`ownerUserId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wish_wishlistId_ownerUserId` ON `wish` (`wishlistId`, `ownerUserId`)")

        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) {
                error("A row of `${cursor.getString(0)}` refers to a row that does not exist or is another owner's")
            }
        }
    }
}

private val WISHLIST_COLUMNS = listOf(
    "id", "ownerUserId", "personId", "name", "version", "remoteState", "createdAt", "updatedAt", "deletedAt",
)

private val WISH_V4_COLUMNS = listOf(
    "id", "wishlistId", "name", "note", "estimatedPriceAmountMinor", "estimatedPriceCurrencyCode",
    "productUrl", "photoPath", "version", "remoteState", "createdAt", "updatedAt", "deletedAt",
)

/** A table [MIGRATION_3_4] rebuilds, as Room defines it in v4. */
private class RebuiltTable(
    val name: String,
    val definition: String,
    val columns: List<String>,
    val indexedColumns: List<String>,
) {
    fun rebuild(db: SupportSQLiteDatabase) {
        val columnList = columns.joinToString { "`$it`" }
        db.execSQL("CREATE TABLE `${name}_new` ($definition)")
        db.execSQL("INSERT INTO `${name}_new` ($columnList) SELECT $columnList FROM `$name`")
        db.execSQL("DROP TABLE `$name`")
        db.execSQL("ALTER TABLE `${name}_new` RENAME TO `$name`")
        indexedColumns.forEach { column ->
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${name}_$column` ON `$name` (`$column`)")
        }
    }
}

// Parents before children. The definitions are Room's v4 schema, copied verbatim.
private val IMMEDIATE_FOREIGN_KEY_TABLES = listOf(
    RebuiltTable(
        name = "occasion",
        definition =
            "`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `personId` TEXT, `type` TEXT NOT NULL, " +
            "`title` TEXT, `day` INTEGER NOT NULL, `month` INTEGER NOT NULL, `year` INTEGER, " +
            "`repeatsYearly` INTEGER NOT NULL, `version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION ",
        columns = listOf(
            "id", "ownerUserId", "personId", "type", "title", "day", "month", "year", "repeatsYearly",
            "version", "remoteState", "createdAt", "updatedAt", "deletedAt",
        ),
        indexedColumns = listOf("ownerUserId", "personId"),
    ),
    RebuiltTable(
        name = "gift_idea",
        definition =
            "`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `recipientPersonId` TEXT NOT NULL, " +
            "`occasionId` TEXT, `name` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`estimatedPriceAmountMinor` INTEGER, `estimatedPriceCurrencyCode` TEXT, " +
            "`actualPriceAmountMinor` INTEGER, `actualPriceCurrencyCode` TEXT, `photoPath` TEXT, " +
            "`productUrl` TEXT, `note` TEXT, `version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `archivedAt` INTEGER, " +
            "`deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`recipientPersonId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
            "FOREIGN KEY(`occasionId`) REFERENCES `occasion`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION ",
        columns = listOf(
            "id", "ownerUserId", "recipientPersonId", "occasionId", "name", "status",
            "estimatedPriceAmountMinor", "estimatedPriceCurrencyCode", "actualPriceAmountMinor",
            "actualPriceCurrencyCode", "photoPath", "productUrl", "note", "version", "remoteState",
            "createdAt", "updatedAt", "archivedAt", "deletedAt",
        ),
        indexedColumns = listOf("ownerUserId", "recipientPersonId", "occasionId"),
    ),
    RebuiltTable(
        name = "gift",
        definition =
            "`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `direction` TEXT NOT NULL, `giverPersonId` TEXT, " +
            "`recipientPersonId` TEXT, `occasionId` TEXT, `sourceGiftIdeaId` TEXT, `name` TEXT NOT NULL, " +
            "`givenAt` TEXT NOT NULL, `priceAmountMinor` INTEGER, `priceCurrencyCode` TEXT, `photoPath` TEXT, " +
            "`productUrl` TEXT, `note` TEXT, `version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`giverPersonId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
            "FOREIGN KEY(`recipientPersonId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
            "FOREIGN KEY(`occasionId`) REFERENCES `occasion`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
            "FOREIGN KEY(`sourceGiftIdeaId`) REFERENCES `gift_idea`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION ",
        columns = listOf(
            "id", "ownerUserId", "direction", "giverPersonId", "recipientPersonId", "occasionId",
            "sourceGiftIdeaId", "name", "givenAt", "priceAmountMinor", "priceCurrencyCode", "photoPath",
            "productUrl", "note", "version", "remoteState", "createdAt", "updatedAt", "deletedAt",
        ),
        indexedColumns = listOf("ownerUserId", "giverPersonId", "recipientPersonId", "occasionId", "sourceGiftIdeaId"),
    ),
    RebuiltTable(
        name = "wishlist",
        definition =
            "`id` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `personId` TEXT, `name` TEXT NOT NULL, " +
            "`version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION ",
        columns = listOf(
            "id", "ownerUserId", "personId", "name", "version", "remoteState", "createdAt", "updatedAt",
            "deletedAt",
        ),
        indexedColumns = listOf("ownerUserId", "personId"),
    ),
    RebuiltTable(
        name = "wish",
        definition =
            "`id` TEXT NOT NULL, `wishlistId` TEXT NOT NULL, `name` TEXT NOT NULL, `note` TEXT, " +
            "`estimatedPriceAmountMinor` INTEGER, `estimatedPriceCurrencyCode` TEXT, `productUrl` TEXT, " +
            "`photoPath` TEXT, `version` INTEGER NOT NULL, `remoteState` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`wishlistId`) REFERENCES `wishlist`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION ",
        columns = listOf(
            "id", "wishlistId", "name", "note", "estimatedPriceAmountMinor", "estimatedPriceCurrencyCode",
            "productUrl", "photoPath", "version", "remoteState", "createdAt", "updatedAt", "deletedAt",
        ),
        indexedColumns = listOf("wishlistId"),
    ),
)
