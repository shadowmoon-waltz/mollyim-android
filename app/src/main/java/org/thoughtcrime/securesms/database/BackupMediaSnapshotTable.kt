/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.updateAll
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject

/**
 * When we delete attachments locally, we can't immediately delete them from the archive CDN. This is because there is still a backup that exists that
 * references that attachment -- at least until a new backup is made.
 *
 * So, this table maintains a snapshot of the media present in the last backup, so that we know what we can and can't delete from the archive CDN.
 */
class BackupMediaSnapshotTable(context: Context, database: SignalDatabase) : DatabaseTable(context, database) {
  companion object {

    const val TABLE_NAME = "backup_media_snapshot"

    private const val ID = "_id"

    /**
     * Generated media id matching that of the attachments table.
     */
    private const val MEDIA_ID = "media_id"

    /**
     * CDN where the data is stored
     */
    private const val CDN = "cdn"

    /**
     * Unique backup snapshot sync time. These are expected to increment in value
     * where newer backups have a greater backup id value.
     */
    @VisibleForTesting
    const val LAST_SYNC_TIME = "last_sync_time"

    /**
     * Pending sync time, set while a backup is in the process of being exported.
     */
    @VisibleForTesting
    const val PENDING_SYNC_TIME = "pending_sync_time"

    /**
     * Whether or not this entry is for a thumbnail.
     */
    const val IS_THUMBNAIL = "is_thumbnail"

    /**
     * Timestamp when media was last seen on archive cdn. Can be reset to default.
     */
    const val LAST_SEEN_ON_REMOTE_TIMESTAMP = "last_seen_on_remote_timestamp"

    /**
     * The remote digest for the media object. This is used to find matching attachments in the attachment table when necessary.
     */
    const val REMOTE_DIGEST = "remote_digest"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MEDIA_ID TEXT UNIQUE,
        $CDN INTEGER,
        $LAST_SYNC_TIME INTEGER DEFAULT 0,
        $PENDING_SYNC_TIME INTEGER,
        $IS_THUMBNAIL INTEGER DEFAULT 0,
        $REMOTE_DIGEST BLOB NOT NULL,
        $LAST_SEEN_ON_REMOTE_TIMESTAMP INTEGER DEFAULT 0
      )
    """.trimIndent()
  }

  /**
   * Writes the set of media items that are slated to be referenced in the next backup, updating their pending sync time.
   * Will insert multiple rows per object -- one for the main item, and one for the thumbnail.
   */
  fun writePendingMediaObjects(mediaObjects: Sequence<ArchiveMediaItem>, pendingSyncTime: Long) {
    mediaObjects
      .chunked(SqlUtil.MAX_QUERY_ARGS)
      .forEach { chunk ->
        writePendingMediaObjectsChunk(
          chunk.map { MediaEntry(it.mediaId, it.cdn, it.digest, isThumbnail = false) },
          pendingSyncTime
        )

        writePendingMediaObjectsChunk(
          chunk.map { MediaEntry(it.thumbnailMediaId, it.cdn, it.digest, isThumbnail = true) },
          pendingSyncTime
        )
      }
  }

  /**
   * Commits the pending sync time to the last sync time. This is called once a backup has been successfully uploaded.
   */
  fun commitPendingRows() {
    writableDatabase.execSQL("UPDATE $TABLE_NAME SET $LAST_SYNC_TIME = $PENDING_SYNC_TIME")
  }

  fun getPageOfOldMediaObjects(currentSyncTime: Long, pageSize: Int): Set<ArchivedMediaObject> {
    return readableDatabase.select(MEDIA_ID, CDN)
      .from(TABLE_NAME)
      .where("$LAST_SYNC_TIME < ? AND $LAST_SYNC_TIME = $PENDING_SYNC_TIME", currentSyncTime)
      .limit(pageSize)
      .run()
      .readToSet {
        ArchivedMediaObject(mediaId = it.requireNonNullString(MEDIA_ID), cdn = it.requireInt(CDN))
      }
  }

  fun deleteMediaObjects(mediaObjects: Collection<ArchivedMediaObject>) {
    val query = SqlUtil.buildFastCollectionQuery(MEDIA_ID, mediaObjects.map { it.mediaId })

    writableDatabase.delete(TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
  }

  /**
   * Given a list of media objects, find the ones that we have no knowledge of in our local store.
   */
  fun getMediaObjectsThatCantBeFound(objects: List<ArchivedMediaObject>): Set<ArchivedMediaObject> {
    if (objects.isEmpty()) {
      return emptySet()
    }

    val queries: List<SqlUtil.Query> = SqlUtil.buildCollectionQuery(
      column = MEDIA_ID,
      values = objects.map { it.mediaId },
      collectionOperator = SqlUtil.CollectionOperator.NOT_IN,
      prefix = "$IS_THUMBNAIL = 0 AND "
    )

    val out: MutableSet<ArchivedMediaObject> = mutableSetOf()

    for (query in queries) {
      out += readableDatabase
        .select(MEDIA_ID, CDN)
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .readToSet {
          ArchivedMediaObject(
            mediaId = it.requireNonNullString(MEDIA_ID),
            cdn = it.requireInt(CDN)
          )
        }
    }

    return out
  }

  /**
   * Given a list of media objects, find the ones that we have no knowledge of in our local store.
   */
  fun getMediaObjectsWithNonMatchingCdn(objects: List<ArchivedMediaObject>): List<CdnMismatchResult> {
    if (objects.isEmpty()) {
      return emptyList()
    }

    val inputValues = objects.joinToString(separator = ", ") { "('${it.mediaId}', ${it.cdn})" }
    return readableDatabase.rawQuery(
      """
      WITH input_pairs($MEDIA_ID, $CDN) AS (VALUES $inputValues)
      SELECT a.$REMOTE_DIGEST, b.$CDN
      FROM $TABLE_NAME a
      JOIN input_pairs b ON a.$MEDIA_ID = b.$MEDIA_ID
      WHERE a.$CDN != b.$CDN AND a.$IS_THUMBNAIL = 0
      """
    ).readToList { cursor ->
      CdnMismatchResult(
        digest = cursor.requireNonNullBlob(REMOTE_DIGEST),
        cdn = cursor.requireInt(CDN)
      )
    }
  }

  /**
   * Indicate the time that the set of media objects were seen on the archive CDN. Can be used to reconcile our local state with the server state.
   */
  fun markSeenOnRemote(mediaIdBatch: Collection<String>, time: Long) {
    if (mediaIdBatch.isEmpty()) {
      return
    }

    val query = SqlUtil.buildFastCollectionQuery(MEDIA_ID, mediaIdBatch)
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_SEEN_ON_REMOTE_TIMESTAMP to time)
      .where(query.where, query.whereArgs)
      .run()
  }

  /**
   * Get all media objects who were last seen on the remote server before the given time.
   * This is used to find media objects that have not been seen on the CDN, even though they should be.
   *
   * The cursor contains rows that can be parsed into [MediaEntry] objects.
   */
  fun getMediaObjectsLastSeenOnCdnBeforeTime(time: Long): Cursor {
    return readableDatabase
      .select(MEDIA_ID, CDN, REMOTE_DIGEST, IS_THUMBNAIL)
      .from(TABLE_NAME)
      .where("$LAST_SEEN_ON_REMOTE_TIMESTAMP < $time")
      .run()
  }

  /**
   * Resets the [LAST_SEEN_ON_REMOTE_TIMESTAMP] column back to zero. It's a good idea to do this after you have run a sync and used the value, as it can
   * mitigate various issues that can arise from having an incorrect local clock.
   */
  fun clearLastSeenOnRemote() {
    writableDatabase
      .updateAll(TABLE_NAME)
      .values(LAST_SEEN_ON_REMOTE_TIMESTAMP to 0)
      .run()
  }

  private fun writePendingMediaObjectsChunk(chunk: List<MediaEntry>, pendingSyncTime: Long) {
    val values = chunk.map {
      contentValuesOf(
        MEDIA_ID to it.mediaId,
        CDN to it.cdn,
        REMOTE_DIGEST to it.digest,
        IS_THUMBNAIL to it.isThumbnail.toInt(),
        PENDING_SYNC_TIME to pendingSyncTime
      )
    }

    val query = SqlUtil.buildSingleBulkInsert(TABLE_NAME, arrayOf(MEDIA_ID, CDN, REMOTE_DIGEST, IS_THUMBNAIL, PENDING_SYNC_TIME), values)

    writableDatabase.execSQL(
      """
      ${query.where}
      ON CONFLICT($MEDIA_ID) DO UPDATE SET
        $PENDING_SYNC_TIME = EXCLUDED.$PENDING_SYNC_TIME,
        $CDN = EXCLUDED.$CDN
      """,
      query.whereArgs
    )
  }

  class ArchiveMediaItem(
    val mediaId: String,
    val thumbnailMediaId: String,
    val cdn: Int,
    val digest: ByteArray
  )

  class CdnMismatchResult(
    val digest: ByteArray,
    val cdn: Int
  )

  class MediaEntry(
    val mediaId: String,
    val cdn: Int,
    val digest: ByteArray,
    val isThumbnail: Boolean
  ) {
    companion object {
      fun fromCursor(cursor: Cursor): MediaEntry {
        return MediaEntry(
          mediaId = cursor.requireNonNullString(MEDIA_ID),
          cdn = cursor.requireInt(CDN),
          digest = cursor.requireNonNullBlob(REMOTE_DIGEST),
          isThumbnail = cursor.requireBoolean(IS_THUMBNAIL)
        )
      }
    }
  }
}
