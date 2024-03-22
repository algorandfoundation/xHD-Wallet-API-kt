package bip32ed25519

import net.pwall.json.schema.JSONSchema

enum class KeyContext(val value: Int) {
    Address(0),
    Identity(1),
}

enum class Encoding {
    CBOR,
    MSGPACK,
    BASE64,
    NONE
}

class DataValidationException(message: String) : Exception(message)

data class SignMetadata(val encoding: Encoding, val schema: JSONSchema)
