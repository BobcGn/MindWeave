package org.example.mindweave.sync

import org.example.mindweave.domain.model.SyncableEntity

class LwwConflictResolver {
    fun shouldApplyRemote(local: SyncableEntity?, remote: SyncableEntity): Boolean {
        local ?: return true
        return when {
            remote.version > local.version -> true
            remote.version < local.version -> false
            else -> remote.updatedAtEpochMs >= local.updatedAtEpochMs
        }
    }
}
