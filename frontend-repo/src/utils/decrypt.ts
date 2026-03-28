// b-system-decrypt.js

import { AES, enc, lib, mode, pad } from 'crypto-js';

const SECRET_KEY = 'your-32-byte-secret-key-12345678';
// 解密身份证号码
function decryptIdCard(ciphertextHex, ivHex, key = SECRET_KEY) {
    const ciphertext = enc.Hex.parse(ciphertextHex);
    const iv = enc.Hex.parse(ivHex);

    const encrypted = lib.CipherParams.create({
        ciphertext: ciphertext
    });

    const decrypted = AES.decrypt(encrypted, enc.Utf8.parse(key), {
        iv: iv,
        mode: mode.CBC,
        padding: pad.Pkcs7
    });

    return decrypted.toString(enc.Utf8);
}

// 加密身份证号码
function encryptIdCard(idCard, key = SECRET_KEY) { 
    const iv = lib.WordArray.random(16);
    const ciphertext = AES.encrypt(idCard, enc.Utf8.parse(key), {
        iv: iv,
        mode: mode.CBC,
        padding: pad.Pkcs7
    });
    // 使用十六进制格式，只包含小写字母和数字
    return {
        ciphertext: ciphertext.ciphertext.toString(enc.Hex),
        iv: iv.toString(enc.Hex)
    };
}

function encryptIdCardWithUserKey(idCard, userId, key = SECRET_KEY) {
    // 获取当前时间戳
    const timestamp = Date.now().toString();
    
    // 将用户ID、时间戳与密钥组合生成新的密钥
    const userSpecificKey = `${userId}_${timestamp}_${key}`.substring(0, 32); // 确保密钥长度为32字节
    
    const iv = lib.WordArray.random(16);
    const ciphertext = AES.encrypt(idCard, enc.Utf8.parse(userSpecificKey), {
        iv: iv,
        mode: mode.CBC,
        padding: pad.Pkcs7
    });
    
    return {
        ciphertext: ciphertext.ciphertext.toString(enc.Hex),
        iv: iv.toString(enc.Hex),
        timestamp: timestamp // 返回时间戳，用于解密时使用
    };
}

// 使用用户密钥解密身份证号码
function decryptIdCardWithUserKey(ciphertextHex, ivHex, userId, timestamp, key = SECRET_KEY) {
    // 将用户ID、时间戳与密钥组合生成新的密钥
    const userSpecificKey = `${userId}_${timestamp}_${key}`.substring(0, 32); // 确保密钥长度为32字节
    
    const ciphertext = enc.Hex.parse(ciphertextHex);
    const iv = enc.Hex.parse(ivHex);

    const encrypted = lib.CipherParams.create({
        ciphertext: ciphertext
    });

    const decrypted = AES.decrypt(encrypted, enc.Utf8.parse(userSpecificKey), {
        iv: iv,
        mode: mode.CBC,
        padding: pad.Pkcs7
    });

    return decrypted.toString(enc.Utf8);
}

export { decryptIdCard, encryptIdCard, encryptIdCardWithUserKey, decryptIdCardWithUserKey };