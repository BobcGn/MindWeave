package org.example.mindweave.sync

import org.example.mindweave.domain.model.SyncableEntity

class LwwConflictResolver {
    fun compareRemoteToLocal(local: SyncableEntity?, remote: SyncableEntity): Int {
        local ?: return 1
        if (remote.version != local.version) {
            return remote.version.compareTo(local.version)
        }
        if (remote.updatedAtEpochMs != local.updatedAtEpochMs) {
            return remote.updatedAtEpochMs.compareTo(local.updatedAtEpochMs)
        }
        return remote.lastModifiedByDeviceId.compareTo(local.lastModifiedByDeviceId)
    }
}
