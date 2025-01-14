//
// Created by test on 14.01.2025.
//

#ifndef PARTISAN_TELEGRAM_ANDROID_ENCRYPTEDCONFIG_H
#define PARTISAN_TELEGRAM_ANDROID_ENCRYPTEDCONFIG_H

#include <string>
#include "NativeByteBuffer.h"
#include "Config.h"

class ConnectionsManager;

class EncryptedConfig {
public:
    EncryptedConfig(int32_t instance, std::string fileName);

    NativeByteBuffer *readConfig();
    void writeConfig(NativeByteBuffer *buffer);

private:
    ConnectionsManager& getConnectionsManager();
    std::string makeEncryptedFileName(std::string fileName);

    Config config;
    std::string fileName;
};


#endif //PARTISAN_TELEGRAM_ANDROID_ENCRYPTEDCONFIG_H
