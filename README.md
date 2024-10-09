# xHD Wallet API - Kotlin

A Kotlin implementation of ARC-0052 Algorand, in accordance with the paper BIP32-Ed25519 Hierarchical Deterministic Keys over a Non-linear Keyspace.

Note that this library has not undergone audit and is not recommended for production use.

Currently there are two folders, one to create an .aar file for use in Android, another to prepare .jar files for use in standard Kotlin desktop use-cases.

## Requirements

| Requirement | Version |
| ----------- | ------- |
| Kotlin      | 1.9.22  |
| Gradle      | 8.6     |
| Android     | 26      |
| Java        | 17      |

## Installation

This library uses a forked version of LazySodium-Java that exposes Ed25519 operations. The fork has been added as a Git Submodule. It needs to be initialized, built and have its .jar files moved into lib/libs at the root level of this repository.

```bash
./initialize.sh
```

Running this will create a build/ folder containing the XHDWalletAPI-Android...aar and XHDWalletAPI-JVM-...jar files respectively. These can be imported in your project.

### Note: For Linux Users

LazySodium-Java bundles up the LibSodium binaries and expose them as part of the .jar files. By default this library will attempt to use the bundled up binaries, but falls back to any system-wide installation of LibSodium.

Currently there is an issue on Linux that means Linux-users will need to rely on a system-wide installation of LibSodium.

For Debian-based Linux distros:

```bash
apt-get install -y libsodium-dev
```

You might be required to install using `sudo` privileges.

## CI/CD

Github Workflows are used. `./initialize.sh`is called, which attempts to build the Android and Desktop libraries and output .aar respectively .jar files. By default `./gradlew build` also runs `./gradlew test`.

### Creating a Release

This project follows [Semantic Versioning](https://semver.org) guidelines and uses `semantic-release` to enforce it.

To create a new release, merge off of `main` into the release branch, e.g. `release/1.x`. `semantic-release` will run, calculate the next version number based off of the commits, create a corresponding tag and produce a release. The release notes will have been populated from the older commits.

Afterwards, merge the branch back into `main`. `gradle.properties` which contains the version read by XHDWalletAPI-Android, XHDWalletAPI-JVM and sharedModule will have also been updated by `semantic-release`, ensuring the repository release version on GitHub is in-sync with the versions for the respective modules.

## How to Use

Below we refer to XHDWalletAPIJVM but the same examples work for XHDWalletAPIAndroid.

Initialize an instance of XHDWalletAPIJVM with seed bytes:

```kotlin
val alice = XHDWalletAPIJVM(seedBytes)
```

Consider using a BIP-39 compatible library like `cash.z.ecc.android:kotlin-bip39` to use a seed phrase instead:

```kotlin
val seed = MnemonicCode(
  "salon zoo engage submit smile frost later decide wing sight chaos renew lizard rely canal coral scene hobby scare step bus leaf tobacco slice".toCharArray())
val alice = XHDWalletAPIJVM(seed.toSeed())
```

Obviously do NOT make use of that seed phrase!

ARC-0052 follows BIP-44 derivation paths, which havve the following fields:

- Standard: 44', for BIP-44. Cannot be set manually.
- Coin-type: Which blockchain? 283' corresponds to an Algorand address, 0' corresponds to identity. We represent this with the KeyContext enum.
- Account: Increment to separate the seed into "accounts".
- Change: Typically 0, but see the notes below.
- Index: Index to increment

Regarding the apostrophes - this is referred to as "hardening", setting the top bit. 44' thus refers to 44 + 2^32.

Regarding coin-type 0': This is typically also reserved for Bitcoin, but the W3C has also granted it to identity. Note that in practice, as this is an _Ed25519_-based implementation, and Bitcoin relies on secp256k1, it is not possible to use this library to inadvertently generate Bitcoin-compatible addresses when generating identity addresses.

Regarding change: the original purpose was to set for "external" (= 0) and "internal" (= 1), i.e. whether the address is meant to be visible outside the wallet or not (to receive payments) or to receive change. This is relevant for the UTXO-model of chains.

In Algorand however, there is an opportunity to assign change values to specific apps that require generation of one-time keys. E.g., a private DAO voting tool using ring signatures will need to generate a new key pair and submit the PK for each vote. The DAO could choose to claim any random number between 0 - (2^32-1), so that the wallet will set that number for the change field. This would be helpful if a user loads the seed into a new wallet and wishes to interact with the same private DAO voting tool - for discovery and displaying of past voting, as well as to avoid accidentally generating and submitting the same keypair as previous.

### Get publicKey

Consider the derivation path `m'/44'/283'/0'/0/0`. This corresponds to:

```kotlin
val publicKey = alice.keyGen(KeyContext.Address, 0u, 0u, 0u)
```

This returns the public key.

### Sign arbitrary data

The user might wish to sign a 32 byte nonce:

```kotlin
val nonce = """
  {"0": 255, "1": 103, "2": 26, "3": 222, "4": 7, "5": 86, "6": 55, "7": 95,
    "8": 197, "9": 179, "10": 249, "11": 252, "12": 232, "13": 252, "14": 176,
    "15": 39, "16": 112, "17": 131, "18": 52, "19": 63, "20": 212, "21": 58,
    "22": 226, "23": 89, "24": 64, "25": 94, "26": 23, "27": 91, "28": 128,
  "29": 143, "30": 123, "31": 27}""" .trimIndent().toByteArray()

val publicKey = alice.keyGen(KeyContext.Address, 0u, 0u, 0u)

val authSchema = JSONSchema.parseFile("src/test/resources/auth.request.json")

val metadata = SignMetadata(Encoding.NONE, authSchema)

val signature = alice.signData(KeyContext.Address, 0u, 0u, 0u, nonce, metadata)

assert(alice.verifyWithPublicKey(signature, nonce, pk))
```

### ECDH

The user might also wish to generate a shared secret with someone else:

Alice's PoV:

```kotlin
val aliceKey = alice.keyGen(KeyContext.Address, 0u, 0u, 0u)
val sharedSecret = alice.ECDH(KeyContext.Address, 0u, 0u, 0u, bobKey, true)
```

Bob's PoV:

```kotlin
val bobKey = bob.keyGen(KeyContext.Address, 0u, 0u, 0u)
val sharedSecret = bob.ECDH(KeyContext.Address, 0u, 0u, 0u, aliceKey, false)
```

Underneath the hood the Ed25519 PKs are turned into Curve25519 format.

Note that ECDH involves hashing a concatenation of a shared point with Alice's and Bob's respective public keys (converted to Curve25519). They'll need to agree before-hand whose public key should go first in this concatenation.

They can then use this shared secret for encrypting data.

### Signing An Algorand Transaction

Alice can turn her public key into an Algorand encoded address using encodeAddress from utils:

```kotlin
val alicePK = alice.keyGen(KeyContext.Address, 0u, 0u, 0u)
val aliceAddress = encodeAddress(alicePK)
println("Alice's Algorand Address: ${aliceAddress.toString()}")
```

Assume that it gets funded by someone. Alice can then start signing her own transactions sending some algoAmount to some receiver:

```kotlin
// Let's have Alice send a tx!

// Construct the TX:
val tx = Transaction.PaymentTransactionBuilder()
  .lookupParams(algod) // lookup fee, firstValid, lastValid
  .sender(aliceAddress)
  .receiver(receiver)
  .amount(algoAmount)
  .noteUTF8("Keep the change!")
  .build()

// Sign the TX with this library:
val sig = alice.signAlgoTransaction(
    KeyContext.Address,
    0u,
    0u,
    0u,
    tx.bytesToSign()
)

// Turn the sig into Algorand SDK's Signature type
val txSig = Signature(sig)

// Combine the sig with the constructed TX using SDK's SignedTransaction()
val stx = SignedTransaction(tx, txSig, tx.txID())

// Encode to msgpack bytes
val stxBytes = Encoder.encodeToMsgPack(stx)

// Post the SignedTransaction msgpack bytes to AlgoD
val post = algod.RawTransaction().rawtxn(stxBytes).execute()

if (!post.isSuccessful) { // Check if there was an issue
    throw RuntimeException("Failed to post transaction")
}

// We confirmed that Algod received the signed transaction. It has begun to propogate it to the network.

// We wait for confirmation:
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

println("Transaction ID: ${post.body()?.txId}")
```

### Deriving Child Public Keys

You can also utilize `deriveKey` to derive extended public keys by setting `isPrivate: false`, thus allowing `deriveChildNodePublic` to softly derive `N` descendant public keys / addresses using a single extended key / root. A typical use case is for producing one-time addresses, either to calculate for yourself in an insecure environment, or to calculate someone else's one time addresses.

> [!IMPORTANT]
> We distinguish between our 32 byte public key (pk) and the 64 byte extended public key (xpk) where xpk is used to derive child nodes in `deriveChildNodePublic` and `deriveChildNodePrivate`. The xpk is a concatenation of the pk and the 32 byte chaincode which serves as a key for the HMAC functions.
>
> **xpk should be kept secret** unless you want to allow someone else to derive descendant keys.

Child public key derivation is relevant at the unhardened levels, e.g. in BIP44 get it at the Account level and then derive publicly for Change and keyIndex. Alternatively get it at the Change-level and only derive for keyIndex.

The following example uses the Bip39 library.

```kotlin
val seed = MnemonicCode(
            "salon zoo engage submit smile frost later decide wing sight chaos renew lizard rely canal coral scene hobby scare step bus leaf tobacco slice".toCharArray())
val bip44Path =
  listOf(
    XHDWalletAPIBase.harden(44u),
    XHDWalletAPIBase.harden(283u),
    XHDWalletAPIBase.harden(0u),
    0u,
  ) // Path to Change = 0

val walletRoot =
  c.deriveKey(
    XHDWalletAPIBase.fromSeed(seed.toSeed()),
    bip44Path,
    false,
  )
```

The output of `deriveKey` is an xpk at the Change-level (`m / 44' / 283' / 0' / 0`) which can be shared. A counter party can then do the following,

```kotlin
val derivedKey = c.deriveChildNodePublic(walletRoot, 0u)
```

and derive the descendant child pk/address at the keyIndex specified, in this case 0. Up to 2^31 are allowed, i.e. ~2 billion.

## Derivation Types

This library supports two derivation types, called `Khovratovic` and `Peikert`. The default option is the `Peikert`option, but `Khovratovich` is supported to allow for interoperability with other Ed25519 HD wallet libraries implementing `BIP32-Ed25519 Hierarchical Deterministic Keys over a Non-linear Keyspace` by Dmitry Khovratovich and Jason Law.

The difference lies in how many bits of randomization are performed when deriving a new child key. `Khovratovic` keeps 32 bits (4 bytes) and randomizes `256-32=224`bits. `Peikert` on the other hand keeps 9 bits and randomizes `256-9 = 254` bits at each level. The `Peikert` derivation type is thus more secure, but only allows for 8 safe levels of derivation, whereas the `Khovratovic` allows for 2^26.

In practice however, common standards like the BIP44 only have 5 levels of derivation, rendering the millions of additional levels useless. Note that the 3 last levels, Account, Change and keyIndex, allow for many billions of possible addresses - allow from a single seed.

## Running tests

This Kotlin project uses Gradle.

```bash
./gradlew test
```

The command above will run tests expected to work without access to an Algorand Localnet/Sandbox running at localhost.

The following command however will run additional tests (testing signAlgoTransaction()) that involve making actual on-chain transactions.

```bash
./gradlew testWithAlgorandSandbox
```

Using algokit, run the following each time to reset account balances:

```bash
algokit localnet reset && ./gradlew testWithAlgorandSandbox
```

As part of the normal test suite we test whether the assumption that we can have a max of 8 levels of security is correct or not for the Peikert derivation type. The same can be done for Khovratovic. However, as that would require `./gradlew test` to take an additional ~30 secs, it is required to run the following:

```bash
./gradlew testWithKhovratovichSafetyDepth
```

It will run the same as `./gradle test` (no Sandbox tests) but with the additional check for the Khovratovich derivation type safety depth.

## License

Copyright 2024 Algorand Foundation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
