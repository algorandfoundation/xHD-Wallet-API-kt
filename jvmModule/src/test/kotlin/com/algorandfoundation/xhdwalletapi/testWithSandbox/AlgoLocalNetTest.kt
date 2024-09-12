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

package com.algorandfoundation.xhdwalletapi

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.toSeed
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.crypto.Signature
import com.algorand.algosdk.kmd.client.KmdClient
import com.algorand.algosdk.kmd.client.api.KmdApi
import com.algorand.algosdk.kmd.client.model.*
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.common.IndexerClient
import kotlin.collections.component1
import kotlin.test.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance

class AlgoLocalNetTest {
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    internal class AlgoSDKTests {

        private lateinit var alice: XHDWalletAPIJVM
        private lateinit var algod: AlgodClient
        private lateinit var indexer: IndexerClient
        private lateinit var token: String
        private lateinit var kmd: KmdApi

        // Based off of README instructions:
        // https://github.com/algorand/java-algorand-sdk/tree/develop

        // Obviously requires a valid Algorand node running on localhost, e.g. using
        // algokit sandbox/localnet

        @BeforeAll
        @Tag("sandbox")
        fun setup() {
            // Setup Alice's HD wallet
            val aliceSeed =
                    MnemonicCode(
                                    "exact remain north lesson program series excess lava material second riot error boss planet brick rotate scrap army riot banner adult fashion casino bamboo".toCharArray()
                            )
                            .toSeed()

            alice = XHDWalletAPIJVM(aliceSeed)

            // Token to sandbox
            token = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            // Algod
            algod = AlgodClient("http://localhost", 4001, token)
            // Indexer
            indexer = IndexerClient("http://localhost", 8980)
            // KMD
            val kmdClient = KmdClient()
            kmdClient.setBasePath("http://localhost:4002")
            kmdClient.setApiKey(token)
            kmd = KmdApi(kmdClient)
        }

        fun getWalletHandle(): String {
            var walletHandle = ""
            for (w in kmd.listWallets().wallets) {
                if (w.name == "unencrypted-default-wallet") {
                    val tokenreq = InitWalletHandleTokenRequest()
                    tokenreq.walletId = w.id
                    tokenreq.walletPassword = ""
                    walletHandle = kmd.initWalletHandleToken(tokenreq).walletHandleToken
                }
            }
            return walletHandle
        }

        fun getKMDAddresses(): MutableList<Address> {
            val walletHandle = getWalletHandle()
            // Get accounts from wallet
            val accounts = mutableListOf<Address>()
            val keysRequest = ListKeysRequest()
            keysRequest.walletHandleToken = walletHandle
            for (addr in kmd.listKeysInWallet(keysRequest).addresses) {
                accounts.add(Address(addr))
            }

            return accounts
        }

        fun fundAddressWithKMDAccount(recipient: String, amountMicroAlgo: Long) {
            // Get accounts from sandbox.
            // Get the wallet handle

            val accounts = getKMDAddresses()
            // Create a payment transaction
            val tx =
                    Transaction.PaymentTransactionBuilder()
                            .lookupParams(algod) // lookup fee, firstValid, lastValid
                            .sender(accounts.get(0))
                            .receiver(recipient)
                            .amount(amountMicroAlgo)
                            .noteUTF8("Fund transaction!")
                            .build()

            // Sign with KMD
            val txReq = SignTransactionRequest()

            txReq.transaction = Encoder.encodeToMsgPack(tx)
            txReq.walletHandleToken = getWalletHandle()
            txReq.walletPassword = ""

            val stxBytes = kmd.signTransaction(txReq).signedTransaction
            val stx = Encoder.decodeFromMsgPack(stxBytes, SignedTransaction::class.java)
            assert(stxBytes.contentEquals(Encoder.encodeToMsgPack(stx))) {
                "stx1aBytes information lost in MsgPack encoding/decoding"
            }

            postSignedTransaction(stxBytes)
        }

        fun lookUpAddressBalance(address: Address): Long {
            val accountInfo = algod.AccountInformation(address).execute()
            if (!accountInfo.isSuccessful) {
                throw RuntimeException("Failed to lookup account")
            }
            return accountInfo.body()?.amount as Long
        }

        fun postSignedTransaction(stxBytes: ByteArray): String {
            val post = algod.RawTransaction().rawtxn(stxBytes).execute()

            if (!post.isSuccessful) {
                throw RuntimeException("Failed to post transaction")
            }

            // Wait for confirmation
            var done = false
            while (!done) {
                val txInfo = algod.PendingTransactionInformation(post.body()?.txId).execute()
                if (!txInfo.isSuccessful) {
                    throw RuntimeException("Failed to check on tx progress")
                }
                if (txInfo.body()?.confirmedRound != null) {
                    done = true
                }
            }

            return post.body()?.txId as String
        }

        @Test
        @Tag("sandbox")
        fun verifyBase32AddressesDifferent() {
            val HDAddresses = mutableListOf<Address>()
            for (i in 0..5) {
                for (j in 0..5) {
                    for (k in 0..5) {
                        for (context in KeyContext.values()) {
                            HDAddresses.add(
                                    Address(
                                            alice.keyGen(
                                                    context,
                                                    k.toUInt(),
                                                    j.toUInt(),
                                                    i.toUInt()
                                            )
                                    )
                            )
                        }
                    }
                }
            }
            assert(HDAddresses.size == HDAddresses.distinct().size) {
                "Different Bip44 paths produced duplicate addresses"
            }

            assert(
                    HDAddresses.get(0)
                            .toString()
                            .equals("I7V63MENRB7L4K53PQGYRFQFI7ZWXD3N53XIGP5THNNT6BSAYWBFYGX4DE")
            ) { "First address does not correspond to hardcoded value" }
        }

        @Test
        @Tag("sandbox")
        fun verifyECDHFromAdresses() {
            // Verify that ECDH works when grabbing someone's Algorand base32 address

            val aliceKey0 = Address("QR5KWDZZ7B4ZHVHZGHH5HS75HWZKU4HF2QL3BILIWR675FQC3WJXANXW7M")
            val aliceKey1 = Address("FSD4BYQTIUBVB6X6MT44CPF2VA7W5ZCAZDEFJGDTOWSG2UPJ6K52IXVFDI")

            val alice0SharedSecret =
                    alice.ECDH(KeyContext.Identity, 0u, 0u, 0u, aliceKey1.getBytes(), true)
            val alice1SharedSecret =
                    alice.ECDH(KeyContext.Identity, 1u, 0u, 0u, aliceKey0.getBytes(), false)

            assert(alice0SharedSecret.contentEquals(alice1SharedSecret)) {
                "Different shared secret generat"
            }

            assert(
                    alice0SharedSecret.contentEquals(
                            helperStringToByteArray(
                                    "131, 42, 24, 10, 19, 49, 210, 175, 170, 10, 255, 222, 54, 147, 2, 30, 212, 160, 172, 18, 130, 186, 219, 160, 136, 37, 39, 213, 96, 42, 90, 34"
                            )
                    )
            ) { "Shared secret different from hardcoded" }
        }

        @Test
        @Tag("sandbox")
        fun verifySignAlgorandTx() {
            val alicePK = alice.keyGen(KeyContext.Address, 0u, 0u, 0u)
            val aliceAddress = Address(alicePK)
            val algo = 1000000L // microAlgos
            val algoFee = 1000L // microAlgos

            // Fund Alice's balance if it has less than 100 Algos
            if (lookUpAddressBalance(aliceAddress) < 100000 * algo) {
                fundAddressWithKMDAccount(
                        aliceAddress.toString(),
                        100 * algo
                ) // Should fund with existing KMD account
            }

            // Check Alice's account
            val aliceBalanceStart = lookUpAddressBalance(aliceAddress)
            val accounts = getKMDAddresses()

            // Let's have Alice send a tx!
            val tx =
                    Transaction.PaymentTransactionBuilder()
                            .lookupParams(algod) // lookup fee, firstValid, lastValid
                            .sender(aliceAddress)
                            .receiver(accounts.get(0))
                            .amount(10 * algo)
                            .noteUTF8("test transaction!")
                            .build()

            // Sign with Alice's key
            val pk = alice.keyGen(KeyContext.Address, 0u, 0u, 0u)
            val pkAddressSDK = Address(pk)

            // compare address encoding in AlgoSDK with this package
            assert(pkAddressSDK.toString() == encodeAddress(pk)) {
                "This package's address encoding does not match SDK's address encoding"
            }

            val txSig =
                    Signature(
                            alice.signAlgoTransaction(
                                    KeyContext.Address,
                                    0u,
                                    0u,
                                    0u,
                                    tx.bytesToSign()
                            )
                    )

            val stx = SignedTransaction(tx, txSig, tx.txID())

            if (tx.sender != pkAddressSDK) {
                stx.authAddr(pkAddressSDK)
            }

            val stxBytes = Encoder.encodeToMsgPack(stx)

            // Post TX to the chain
            postSignedTransaction(stxBytes)

            // Check Alice's account
            val aliceBalanceEnd = lookUpAddressBalance(aliceAddress)

            assert(aliceBalanceStart - aliceBalanceEnd == 10 * algo + algoFee) {
                "Alice's balance did not decrease by 10 Algos + fee"
            }
        }
    }
}
