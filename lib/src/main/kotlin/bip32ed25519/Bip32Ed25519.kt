/*
 * Copyright (c) Algorand Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bip32ed25519

import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.crypto.Signature
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.LibraryLoader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import net.pwall.json.schema.JSONSchema
import org.msgpack.jackson.dataformat.MessagePackFactory

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

class Bip32Ed25519(private var seed: ByteArray) {
    companion object {

        // Load it once statically and use it for the lifetime of the application
        val lazySodium: LazySodiumJava =
                LazySodiumJava(SodiumJava(LibraryLoader.Mode.PREFER_BUNDLED))

        /**
         * Harden a number (set the highest bit to 1) Note that the input is UInt and the output is
         * also UInt
         *
         * @param num
         * @returns
         */
        fun harden(num: UInt): UInt = 0x80000000.toUInt() + num

        /*
         * Get the BIP44 path from the context, account and keyIndex
         *
         * @param context
         * @param account
         * @param keyIndex
         * @returns
         */

        fun getBIP44PathFromContext(
                context: KeyContext,
                account: UInt,
                change: UInt,
                keyIndex: UInt
        ): List<UInt> {
            return when (context) {
                KeyContext.Address ->
                        listOf(harden(44u), harden(283u), harden(account), change, keyIndex)
                KeyContext.Identity ->
                        listOf(harden(44u), harden(0u), harden(account), change, keyIndex)
            }
        }

        /**
         * Implementation how to validate data with encoding and schema, using base64 as an example
         *
         * @param message
         * @param metadata
         * @returns
         */
        fun validateData(message: ByteArray, metadata: SignMetadata): Boolean {
            // Check for Algorand tags
            if (hasAlgorandTags(message)) {
                return false
            }

            val decoded: ByteArray =
                    when (metadata.encoding) {
                        Encoding.BASE64 -> Base64.getDecoder().decode(message)
                        // Encoding.CBOR ->
                        Encoding.MSGPACK ->
                                ObjectMapper()
                                        .writeValueAsString(
                                                ObjectMapper(MessagePackFactory())
                                                        .readValue(message, Map::class.java)
                                        )
                                        .toByteArray()
                        Encoding.NONE -> message
                        else -> throw IllegalArgumentException("Invalid encoding")
                    }

            // Check after decoding too
            if (hasAlgorandTags(decoded)) {
                return false
            }

            // Validate with schema
            try {
                return metadata.schema.validateBasic(String(decoded)).valid
            } catch (e: Exception) {
                return false
            }
        }

        fun hasAlgorandTags(message: ByteArray): Boolean {
            // Prefixes taken from go-algorand node software code
            // https://github.com/algorand/go-algorand/blob/master/protocol/hash.go
            val prefixes =
                    listOf(
                            "appID",
                            "arc",
                            "aB",
                            "aD",
                            "aO",
                            "aP",
                            "aS",
                            "AS",
                            "B256",
                            "BH",
                            "BR",
                            "CR",
                            "GE",
                            "KP",
                            "MA",
                            "MB",
                            "MX",
                            "NIC",
                            "NIR",
                            "NIV",
                            "NPR",
                            "OT1",
                            "OT2",
                            "PF",
                            "PL",
                            "Program",
                            "ProgData",
                            "PS",
                            "PK",
                            "SD",
                            "SpecialAddr",
                            "STIB",
                            "spc",
                            "spm",
                            "spp",
                            "sps",
                            "spv",
                            "TE",
                            "TG",
                            "TL",
                            "TX",
                            "VO"
                    )
            val messageString = String(message)
            return prefixes.any { messageString.startsWith(it) }
        }

        /**
         * Reference of BIP32-Ed25519 Hierarchical Deterministic Keys over a Non-linear Keyspace
         *
         * @see section V. BIP32-Ed25519: Specification;
         *
         * A) Root keys
         *
         * @param seed
         * - 256 bite seed generated from BIP39 Mnemonic
         * @returns
         * - Extended root key (kL, kR, c) where kL is the left 32 bytes of the root key, kR is the
         * right 32 bytes of the root key, and c is the chain code. Total 96 bytes
         */
        fun fromSeed(seed: ByteArray): ByteArray {
            // k = H512(seed)
            var k = MessageDigest.getInstance("SHA-512").digest(seed)
            var kL = k.sliceArray(0 until 32)
            var kR = k.sliceArray(32 until 64)

            // While the third highest bit of the last byte of kL is not zero
            while (kL[31].toInt() and 0b00100000 != 0) {
                val hmac = Mac.getInstance("HmacSHA512")
                hmac.init(SecretKeySpec(kL, "HmacSHA512"))
                k = hmac.doFinal(kR)
                kL = k.sliceArray(0 until 32)
                kR = k.sliceArray(32 until 64)
            }

            // clamp
            // Set the bits in kL as follows:
            // little Endianess
            kL[0] =
                    (kL[0].toInt() and 0b11111000)
                            .toByte() // the lowest 3 bits of the first byte of kL are cleared
            kL[31] =
                    (kL[31].toInt() and 0b01111111)
                            .toByte() // the highest bit of the last byte is cleared
            kL[31] =
                    (kL[31].toInt() or 0b01000000)
                            .toByte() // the second highest bit of the last byte is set

            // chain root code
            // SHA256(0x01||k)
            val c = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(0x01) + seed)
            return kL + kR + c
        }
    }

    /**
     *
     * @see section V. BIP32-Ed25519: Specification
     *
     * @param kl
     * - The scalar
     * @param cc
     * - chain code
     * @param index
     * - non-hardened ( < 2^31 ) index
     * @returns
     * - (z, c) where z is the 64-byte child key and c is the chain code
     */
    internal fun deriveNonHardened(
            kl: ByteArray,
            cc: ByteArray,
            index: UInt
    ): Pair<ByteArray, ByteArray> {
        val data = ByteBuffer.allocate(1 + 32 + 4)
        data.put(1 + 32, index.toByte())

        val pk = lazySodium.cryptoScalarMultEd25519BaseNoclamp(kl).toBytes()
        data.position(1)
        data.put(pk)

        data.put(0, 0x02)
        val hmac = Mac.getInstance("HmacSHA512")
        hmac.init(SecretKeySpec(cc, "HmacSHA512"))
        val z = hmac.doFinal(data.array())

        data.put(0, 0x03)
        hmac.init(SecretKeySpec(cc, "HmacSHA512"))
        val childChainCode = hmac.doFinal(data.array())

        return Pair(z, childChainCode)
    }

    /**
     *
     * @see section V. BIP32-Ed25519: Specification
     *
     * @param kl
     * - The scalar (a.k.a private key)
     * @param kr
     * - the right 32 bytes of the root key
     * @param cc
     * - chain code
     * @param index
     * - hardened ( >= 2^31 ) index
     * @returns
     * - (z, c) where z is the 64-byte child key and c is the chain code
     */
    internal fun deriveHardened(
            kl: ByteArray,
            kr: ByteArray,
            cc: ByteArray,
            index: UInt
    ): Pair<ByteArray, ByteArray> {
        val indexLEBytes = ByteArray(4) { i -> ((index shr (8 * i)) and 0xFFu).toByte() }
        val data = ByteBuffer.allocate(1 + 64 + 4)
        data.position(1 + 64)
        data.put(indexLEBytes)
        data.position(1)
        data.put(kl)
        data.put(kr)

        data.put(0, 0x00)
        val hmac = Mac.getInstance("HmacSHA512")
        hmac.init(SecretKeySpec(cc, "HmacSHA512"))
        val z = hmac.doFinal(data.array())

        data.put(0, 0x01)
        hmac.init(SecretKeySpec(cc, "HmacSHA512"))
        val childChainCode = hmac.doFinal(data.array())

        return Pair(z, childChainCode)
    }

    /**
     * @see section V. BIP32-Ed25519: Specification;
     *
     * subsections:
     *
     * B) Child Keys and C) Private Child Key Derivation
     *
     * @param extendedKey
     * - extended key (kL, kR, c) where kL is the left 32 bytes of the root key the scalar (pvtKey).
     * kR is the right 32 bytes of the root key, and c is the chain code. Total 96 bytes
     * @param index
     * - index of the child key
     * @returns
     * - (kL, kR, c) where kL is the left 32 bytes of the child key (the new scalar), kR is the
     * right 32 bytes of the child key, and c is the chain code. Total 96 bytes
     */
    internal fun deriveChildNodePrivate(extendedKey: ByteArray, index: UInt): ByteArray {
        val kl = extendedKey.sliceArray(0 until 32)
        val kr = extendedKey.sliceArray(32 until 64)
        val cc = extendedKey.sliceArray(64 until 96)

        val (z, childChainCode) =
                if (index < 0x80000000.toUInt()) deriveNonHardened(kl, cc, index)
                else deriveHardened(kl, kr, cc, index)

        val chainCode = childChainCode.sliceArray(32 until 64)
        val zl = z.sliceArray(0 until 32)
        val zr = z.sliceArray(32 until 64)

        // left = kl + 8 * trunc28(zl)
        // right = zr + kr
        val left =
                (BigInteger(1, kl.reversedArray()) +
                                BigInteger(1, zl.sliceArray(0 until 28).reversedArray()) *
                                        BigInteger.valueOf(8L))
                        .toByteArray()
                        .reversedArray()
                        .let { bytes -> ByteArray(32 - bytes.size) + bytes } // Pad to 32 bytes

        var right =
                (BigInteger(1, kr.reversedArray()) + BigInteger(1, zr.reversedArray()))
                        .toByteArray()
                        .reversedArray()
                        .let { bytes ->
                            bytes.sliceArray(0 until minOf(bytes.size, 32))
                        } // Slice to 32 bytes

        right = right + ByteArray(32 - right.size)

        return ByteBuffer.allocate(96).put(left).put(right).put(chainCode).array()
    }

    /**
     * Derives a child key from the root key based on BIP44 path
     *
     * @param rootKey
     * - root key in extended format (kL, kR, c). It should be 96 bytes long
     * @param bip44Path
     * - BIP44 path (m / purpose' / coin_type' / account' / change / address_index). The ' indicates
     * that the value is hardened
     * @param isPrivate
     * - returns full 64 bytes privatekey (first 32 bytes scalar), false returns 32 byte public key,
     * @returns
     * - The public key of 32 bytes. If isPrivate is true, returns the private key instead.
     */
    internal fun deriveKey(
            rootKey: ByteArray,
            bip44Path: List<UInt>,
            isPrivate: Boolean
    ): ByteArray {
        var derived = this.deriveChildNodePrivate(rootKey, bip44Path[0])
        derived = this.deriveChildNodePrivate(derived, bip44Path[1])
        derived = this.deriveChildNodePrivate(derived, bip44Path[2])
        derived = this.deriveChildNodePrivate(derived, bip44Path[3])

        // Public Key SOFT derivations are possible without using the private key of the parentnode
        // Could be an implementation choice.
        // Example:
        // val nodeScalar: ByteArray = derived.sliceArray(0 until 32)
        // val nodePublic: ByteArray =
        // lazySodium.cryptoScalarMultEd25519BaseNoclamp(nodeScalar).toBytes()
        // val nodeCC: ByteArray = derived.sliceArray(64 until 96)

        // // [Public][ChainCode]
        // val extPub: ByteArray = nodePublic + nodeCC
        // val publicKey: ByteArray = deriveChildNodePublic(extPub, bip44Path[4]).sliceArray(0 until
        // 32)

        derived = this.deriveChildNodePrivate(derived, bip44Path[4])

        if (isPrivate) {
            return derived
        } else {
            return lazySodium
                    .cryptoScalarMultEd25519BaseNoclamp(derived.sliceArray(0 until 32))
                    .toBytes()
        }
    }

    /**
     *
     * @param context
     * - context of the key (i.e Address, Identity)
     * @param account
     * - account number. This value will be hardened as part of BIP44
     * @param keyIndex
     * - key index. This value will be a SOFT derivation as part of BIP44.
     * @returns
     * - public key 32 bytes
     */
    fun keyGen(context: KeyContext, account: UInt, change: UInt, keyIndex: UInt): ByteArray {
        val rootKey: ByteArray = fromSeed(this.seed)
        val bip44Path: List<UInt> = getBIP44PathFromContext(context, account, change, keyIndex)

        return this.deriveKey(rootKey, bip44Path, false)
    }

    /**
     * Sign arbitrary but non-Algorand related data
     * @param context
     * - context of the key (i.e Address, Identity)
     * @param account
     * - account number. This value will be hardened as part of BIP44
     * @param keyIndex
     * - key index. This value will be a SOFT derivation as part of BIP44.
     * @param data
     * - data to be signed in raw bytes
     * @param metadata
     * - metadata object that describes how `data` was encoded and what schema to use to validate
     * against
     *
     * @returns
     * - signature holding R and S, totally 64 bytes
     */
    fun signData(
            context: KeyContext,
            account: UInt,
            change: UInt,
            keyIndex: UInt,
            data: ByteArray,
            metadata: SignMetadata,
    ): ByteArray {

        val valid = validateData(data, metadata)

        if (!valid) { // failed schema validation
            throw DataValidationException("Data validation failed")
        }

        return rawSign(getBIP44PathFromContext(context, account, change, keyIndex), data)
    }

    /**
     * Sign Algorand transaction
     * @param context
     * - context of the key (i.e Address, Identity)
     * @param account
     * - account number. This value will be hardened as part of BIP44
     * @param keyIndex
     * - key index. This value will be a SOFT derivation as part of BIP44.
     * @param tx
     * - Transaction object containing parameters to be signed, e.g. sender, receiver, amount, fee,
     *
     * @returns stx
     * - SignedTransaction object
     */
    fun signAlgoTransaction(
            context: KeyContext,
            account: UInt,
            change: UInt,
            keyIndex: UInt,
            tx: Transaction,
    ): SignedTransaction {

        val prefixEncodedTx = tx.bytesToSign()
        val pk = this.keyGen(context, account, change, keyIndex)
        val pkAddress = Address(pk)

        val txSig =
                Signature(
                        rawSign(
                                getBIP44PathFromContext(context, account, change, keyIndex),
                                prefixEncodedTx.copyOf()
                        )
                )

        val stx = SignedTransaction(tx, txSig, tx.txID())

        if (tx.sender != pkAddress) {
            stx.authAddr(pkAddress)
        }
        return stx
    }

    /**
     * Raw Signing function called by signData and signTransaction
     *
     * Ref: https://datatracker.ietf.org/doc/html/rfc8032#section-5.1.6
     *
     * Edwards-Curve Digital Signature Algorithm (EdDSA)
     *
     * @param bip44Path
     * - BIP44 path (m / purpose' / coin_type' / account' / change / address_index)
     * @param data
     * - data to be signed in raw bytes
     *
     * @returns
     * - signature holding R and S, totally 64 bytes
     */
    fun rawSign(bip44Path: List<UInt>, data: ByteArray): ByteArray {

        val rootKey: ByteArray = fromSeed(this.seed)
        val raw: ByteArray = deriveKey(rootKey, bip44Path, true)

        val scalar = raw.sliceArray(0 until 32)
        val c = raw.sliceArray(32 until 64)

        // \(1): pubKey = scalar * G (base point, no clamp)
        val publicKey = lazySodium.cryptoScalarMultEd25519BaseNoclamp(scalar).toBytes()

        // \(2): r = hash(c + msg) mod q [LE]
        var r = this.safeModQ(MessageDigest.getInstance("SHA-512").digest(c + data))

        // \(4):  R = r * G (base point, no clamp)
        val R = lazySodium.cryptoScalarMultEd25519BaseNoclamp(r).toBytes()

        var h = this.safeModQ(MessageDigest.getInstance("SHA-512").digest(R + publicKey + data))

        // \(5): S = (r + h * k) mod q
        var S =
                this.safeModQ(
                        lazySodium.cryptoCoreEd25519ScalarAdd(
                                r,
                                lazySodium
                                        .cryptoCoreEd25519ScalarMul(h, scalar)
                                        .toByteArray()
                                        .reversedArray()
                        )
                )

        return R + S
    }

    /*
     * SafeModQ is a helper function to ensure that the result of a mod q operation is 32 bytes
     * It wraps around the cryptoCoreEd25519ScalarReduce function, which can accept either BigInteger or ByteArray
     */
    fun safeModQ(input: BigInteger): ByteArray {
        var reduced = lazySodium.cryptoCoreEd25519ScalarReduce(input).toByteArray().reversedArray()
        if (reduced.size < 32) {
            reduced = reduced + ByteArray(32 - reduced.size)
        }
        return reduced
    }

    fun safeModQ(input: ByteArray): ByteArray {
        var reduced = lazySodium.cryptoCoreEd25519ScalarReduce(input).toByteArray().reversedArray()
        if (reduced.size < 32) {
            reduced = reduced + ByteArray(32 - reduced.size)
        }
        return reduced
    }

    /**
     * Wrapper around libsodium basic signature verification
     *
     * Any lib or system that can verify EdDSA signatures can be used
     *
     * @param signature
     * - raw 64 bytes signature (R, S)
     * @param message
     * - raw bytes of the message
     * @param publicKey
     * - raw 32 bytes public key (x,y)
     * @returns true if signature is valid, false otherwise
     */
    fun verifyWithPublicKey(
            signature: ByteArray,
            message: ByteArray,
            publicKey: ByteArray
    ): Boolean {
        return lazySodium.cryptoSignVerifyDetached(signature, message, message.size, publicKey)
    }

    /**
     * Function to perform ECDH against a provided public key
     *
     * ECDH reference link: https://en.wikipedia.org/wiki/Elliptic-curve_Diffie%E2%80%93Hellman
     *
     * It creates a shared secret between two parties. Each party only needs to be aware of the
     * other's public key. This symmetric secret can be used to derive a symmetric key for
     * encryption and decryption. Creating a private channel between the two parties. Note that you
     * must specify the order of concatenation for the public keys with otherFirst.
     * @param context
     * - context of the key (i.e Address, Identity)
     * @param account
     * - account number. This value will be hardened as part of BIP44
     * @param keyIndex
     * - key index. This value will be a SOFT derivation as part of BIP44.
     * @param otherPartyPub
     * - raw 32 bytes public key of the other party
     * @param meFirst
     * - decide the order of concatenation of the public keys in the shared secret, true: my public
     * key first, false: other party's public key first
     * @returns
     * - raw 32 bytes shared secret
     */
    fun ECDH(
            context: KeyContext,
            account: UInt,
            change: UInt,
            keyIndex: UInt,
            otherPartyPub: ByteArray,
            meFirst: Boolean,
    ): ByteArray {

        val rootKey: ByteArray = fromSeed(this.seed)

        val publicKey: ByteArray = this.keyGen(context, account, change, keyIndex)
        val privateKey: ByteArray =
                this.deriveKey(
                        rootKey,
                        getBIP44PathFromContext(context, account, change, keyIndex),
                        true
                )

        val scalar: ByteArray = privateKey.sliceArray(0 until 32)

        val sharedPoint = ByteArray(32)
        val myCurve25519Key = ByteArray(32)
        val otherPartyCurve25519Key = ByteArray(32)

        lazySodium.convertPublicKeyEd25519ToCurve25519(myCurve25519Key, publicKey)
        lazySodium.convertPublicKeyEd25519ToCurve25519(otherPartyCurve25519Key, otherPartyPub)
        lazySodium.cryptoScalarMult(sharedPoint, scalar, otherPartyCurve25519Key)

        val concatenated: ByteArray

        if (meFirst) {
            concatenated = sharedPoint + myCurve25519Key + otherPartyCurve25519Key
        } else {
            concatenated = sharedPoint + otherPartyCurve25519Key + myCurve25519Key
        }

        val output = ByteArray(32)
        lazySodium.cryptoGenericHash(
                output,
                32,
                concatenated,
                concatenated.size.toLong(),
        )
        return output
    }
}
