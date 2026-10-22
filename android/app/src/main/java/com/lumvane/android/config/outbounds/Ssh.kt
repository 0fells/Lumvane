package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.SshBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

fun SshBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("ssh", server, serverPort, dial, null, env)
    putIfNotEmpty("user", user)
    putIfNotEmpty("password", password)
    putIfNotEmpty("private_key", privateKey)
    putIfNotEmpty("private_key_path", privateKeyPath)
    putIfNotEmpty("private_key_passphrase", privateKeyPassphrase)
    putStrings("host_key", hostKey)
    putStrings("host_key_algorithms", hostKeyAlgorithms)
    putIfNotEmpty("client_version", clientVersion)
}
