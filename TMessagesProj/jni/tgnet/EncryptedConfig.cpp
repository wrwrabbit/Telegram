//
// Created by test on 14.01.2025.
//

#include <sys/stat.h>
#include <openssl/aes.h>
#include <openssl/base64.h>
#include <unistd.h>
#include <iomanip>
#include <errno.h>
#include <cstring>
#include "EncryptedConfig.h"
#include "ConnectionsManager.h"
#include "FileLog.h"
#include "BuffersStorage.h"

EncryptedConfig::EncryptedConfig(int32_t instance, std::string fileName)
: config(instance, fileName)
, fileName(fileName)
{}

ConnectionsManager& EncryptedConfig::getConnectionsManager() {
    return ConnectionsManager::getInstance(config.instanceNum);
}

std::string EncryptedConfig::makeEncryptedFileName(std::string fileName) {
    const std::string& encryptionKeyStr = getConnectionsManager().getConfigEncryptionKey();
    unsigned char iv[16] { 0 };
    iv[0] = config.instanceNum;

    std::vector<uint8_t> keyBytes(encryptionKeyStr.size());
    size_t keyBytesCount;
    EVP_DecodeBase64(keyBytes.data(), &keyBytesCount, keyBytes.size(), (uint8_t*)encryptionKeyStr.data(), encryptionKeyStr.length());

    AES_KEY aeskey;
    AES_set_decrypt_key(keyBytes.data(), 256, &aeskey);
    std::vector<uint8_t> plainNameBytes(fileName.begin(), fileName.end());
    size_t paddingLength = (32 - fileName.length() % 32) % 32;
    if (paddingLength != 0) {
        plainNameBytes.resize(plainNameBytes.size() + paddingLength);
    }

    std::vector<uint8_t> encryptedNameBytes(plainNameBytes.size());
    AES_cbc_encrypt(plainNameBytes.data(), encryptedNameBytes.data(), plainNameBytes.size(), &aeskey, iv, AES_ENCRYPT);

    std::stringstream hexNameStream;
    hexNameStream << std::hex;

    for (unsigned char encryptedNameByte : encryptedNameBytes) {
        hexNameStream << std::setw(2) << std::setfill('0') << (int)encryptedNameByte;
    }

    return hexNameStream.str();
}

NativeByteBuffer *EncryptedConfig::readConfig() {
    std::string plainConfigPath = getConnectionsManager().currentConfigPath + fileName;
    if (!getConnectionsManager().getConfigEncryptionKey().empty()) {
        FILE *file = fopen(plainConfigPath.c_str(), "rb");
        if (file != nullptr) {
            fclose(file);
            config.configPath = plainConfigPath;
        } else {
            config.configPath = getConnectionsManager().currentConfigPath + makeEncryptedFileName(fileName);
        }
    } else {
        config.configPath = plainConfigPath;
    }
    return config.readConfig();
}

void EncryptedConfig::writeConfig(NativeByteBuffer *buffer) {
    std::string plainConfigPath = getConnectionsManager().currentConfigPath + fileName;
    std::string encryptedConfigPath = getConnectionsManager().currentConfigPath + makeEncryptedFileName(fileName);
    std::string *pathToDelete;
    if (!getConnectionsManager().getConfigEncryptionKey().empty()) {
        config.configPath = encryptedConfigPath;
        config.writeConfig(buffer);
        pathToDelete = &plainConfigPath;
    } else {
        config.configPath = plainConfigPath;
        config.writeConfig(buffer);
        pathToDelete = &encryptedConfigPath;
    }

    FILE *file = fopen(pathToDelete->c_str(), "rb");
    if (file != nullptr) {
        fclose(file);
        remove(pathToDelete->c_str());
    }
}