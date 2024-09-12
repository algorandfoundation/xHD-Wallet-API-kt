package com.algorandfoundation.xhdwalletapi

import java.security.MessageDigest
import java.util.Arrays
import net.pwall.json.schema.JSONSchema
import org.apache.commons.codec.binary.Base32

enum class KeyContext(val value: Int) {
  Address(0),
  Identity(1),
}

enum class Bip32DerivationType(val value: Int) {
  Peikert(9),
  Khovratovich(32)
}

enum class Encoding {
  // CBOR, // CBOR is not yet supported across all platforms
  MSGPACK,
  BASE64,
  NONE
}

class BigIntegerOverflowException() :
        IllegalArgumentException("Overflow: 8*zL is larger than 2^255")

class DataValidationException(message: String) : Exception(message)

data class SignMetadata(val encoding: Encoding, val schema: JSONSchema)

fun encodeAddress(bytes: ByteArray): String {
  val lenBytes = 32
  val checksumLenBytes = 4
  val expectedStrEncodedLen = 58

  // compute sha512/256 checksum
  val messageDigest = MessageDigest.getInstance("SHA-512/256")
  val fullHash = messageDigest.digest(bytes)
  val hashedAddr = Arrays.copyOf(fullHash, lenBytes) // Take the first 32 bytes

  // take the last 4 bytes of the hashed address, and append to original bytes
  val checksum = hashedAddr.sliceArray(hashedAddr.size - checksumLenBytes until hashedAddr.size)
  val checksumAddr = bytes + checksum

  // encodeToMsgPack addr+checksum as base32 and return. Strip padding.
  val res = Base32().encodeAsString(checksumAddr).trimEnd('=')
  if (res.length != expectedStrEncodedLen) {
    throw Exception("unexpected address length ${res.length}")
  }
  return res
}
