package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.SyncApi;

public interface SyncService {
    SyncApi.PushResponse push(SyncApi.PushRequest request);

    SyncApi.PullResponse pull(String cursor, int limit);
}
