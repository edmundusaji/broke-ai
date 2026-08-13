package org.edmund.brokeai.service;

public interface PrivateObjectStorage {
    void put(String objectKey, byte[] content);

    byte[] get(String objectKey);

    void delete(String objectKey);
}
