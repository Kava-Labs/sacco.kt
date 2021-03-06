package network.commercio.sacco

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import network.commercio.sacco.encoding.toBase64
import network.commercio.sacco.models.account.AccountData
import network.commercio.sacco.models.chain.NodeInfo
import network.commercio.sacco.models.types.*
import network.commercio.sacco.utils.LCDService

/**
 * Allows to easily sign a [StdTx] object that already contains a message.
 */
object TxSigner {

    /**
     * Signs the given [stdTx] using the data contained inside the given [wallet].
     *
     * NOTE. This method is `suspend` because it needs to ask the network in order to get the current account
     * number and sequence number when composing the message signature.
     */
    suspend fun signStdTx(wallet: Wallet, stdTx: StdTx): StdTx {
        // Get the account data from the network
        val account = LCDService.getAccountData(wallet)

        // Get the node information
        val nodeInfo = LCDService.getNodeInfo(wallet)

        // Sign all messages
        val signatures = getStdSignature(wallet, account, nodeInfo, stdTx.messages, stdTx.fee, stdTx.memo)


        // Assemble the transaction
        return stdTx.copy(signatures = listOf(signatures))
    }

    /**
     * Creates an [StdSignature] object containing the signature value of the given [msgs].
     * When creating the signature, the given [fee] and [memo] are inserted inside the signature, and the whole
     * JSON object is signed using the provided [wallet].
     *
     * Note that in order to properly sign a message, the following operations are performed.
     *
     * 1. The message value keys are sorted alphabetically
     * 2. A [StdSignatureMessage] is created, containing all the necessary data.
     * 3. The [StdSignatureMessage] is converted to a byte array, hashed using the SHA-256 and signed using the private key
     *    present inside the user wallet.
     * 4. The signed data is converted into a Base64 string and put inside a new [StdSignature] object.
     */
    private fun getStdSignature(
        wallet: Wallet,
        account: AccountData,
        nodeInfo: NodeInfo,
        msgs: List<StdMsg>,
        fee: StdFee,
        memo: String
    ): StdSignature {

        // Create the signature object
        val signature = StdSignatureMessage(
            chainId = nodeInfo.info.chainId,
            accountNumber = account.accountNumber.toString(),
            memo = memo,
            msgs = msgs,
            sequence = account.sequence.toString(),
            fee = fee
        )

        // Convert the signature to a JSON and sort it
        val objectMapper = jacksonObjectMapper().apply {
            configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            setSerializationInclusion(JsonInclude.Include.ALWAYS)
        }
        val jsonSignData = objectMapper.writeValueAsString(signature)
        println(jsonSignData)

        // Sign the message
        val signatureData = wallet.signTxData(jsonSignData.toByteArray(Charsets.UTF_8))

        // Get the compressed Base64 public key
        val pubKeyCompressed = wallet.pubKeyPoint.getEncoded(true)

        // Build the StdSignature
        return StdSignature(
            value = signatureData.toBase64(),
            pubKey = StdSignature.PubKey(
                type = "tendermint/PubKeySecp256k1",
                value = pubKeyCompressed.toBase64()
            )
        )
    }
}
