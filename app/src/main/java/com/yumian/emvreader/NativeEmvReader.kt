package com.yumian.emvreader

import android.nfc.tech.IsoDep
import android.util.Log

class NativeEmvReader(private val isoDep: IsoDep) {

    private val logTag = "NativeEmv"

    // Commonly Used APDUs
    private val selectPpse = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E, 0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00)

    fun readTransactions(): List<CardTransaction> {
        val transactions = mutableListOf<CardTransaction>()
        try {
            isoDep.connect()
            isoDep.timeout = 5000 // Increase timeout for log reading

            // 1. Select PPSE to find AIDs
            val ppseResponse = transceive(selectPpse)
            var aids = extractAids(ppseResponse)

            // Fallback for AID if PPSE empty or failed
            if (aids.isEmpty()) {
                 aids = listOf(
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10), // MC
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10), // Visa
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x03, 0x33, 0x01, 0x01), // UP
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x01, 0x08, 0x88.toByte(), 0x88.toByte()) // Special
                )
            }
            Log.d(logTag, "Using AIDs: ${aids.joinToString { it.toHexString() }}")

            for (aid in aids) {
                // Select AID
                val selectAidCmd = buildSelectApdu(aid)
                val fciResponse = transceive(selectAidCmd)
                if (!isSuccess(fciResponse)) continue

                // Check for Log Entry (9F4D) in FCI
                var logEntry = findTlv(fciResponse, 0x9F4D)
                var logFormat = findTlv(fciResponse, 0x9F4F)

                // Get Processing Options (GPO)
                // PDOL in 9F38 tag in FCI
                val pdol = findTlv(fciResponse, 0x9F38)
                val gpoCmd = if (pdol != null) {
                    // Try to construct PDOL data. Simplified: just zeros
                    // PDOL format: Tag Len Tag Len ...
                    val length = calculatePdolLength(pdol)
                    val cmd = ByteArray(4 + 1 + 1 + length + 1) // CLA INS P1 P2 Lc 83 Le [Data]
                    cmd[0] = 0x80.toByte()
                    cmd[1] = 0xA8.toByte()
                    cmd[2] = 0x00
                    cmd[3] = 0x00
                    cmd[4] = (length + 2).toByte() // Lc (83 + Len + Data)
                    cmd[5] = 0x83.toByte()
                    cmd[6] = length.toByte()
                    // Data is 00s
                    cmd[cmd.size-1] = 0x00 // Le
                    cmd
                } else {
                     byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00, 0x00)
                }

                val gpoResponse = transceive(gpoCmd)
                if (isSuccess(gpoResponse)) {
                    // If Log Entry wasn't in FCI, search in GPO response
                     if (logEntry == null) logEntry = findTlv(gpoResponse, 0x9F4D)
                     // If Log Format wasn't in FCI, search in GPO response
                     if (logFormat == null) logFormat = findTlv(gpoResponse, 0x9F4F)

                    // Also try explicit GET DATA for Log Entry (9F4D)
                    if (logEntry == null) {
                         val getData9F4D = byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x4D.toByte(), 0x00)
                         val resp = transceive(getData9F4D)
                         if (isSuccess(resp)) {
                             logEntry = findTlv(resp, 0x9F4D)
                         }
                    }

                     // Also try explicit GET DATA for Log Format (9F4F)
                    if (logFormat == null) {
                         val getData9F4F = byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x4F.toByte(), 0x00)
                         val resp = transceive(getData9F4F)
                         if (isSuccess(resp)) {
                             logFormat = findTlv(resp, 0x9F4F)
                         }
                    }

                    if (logEntry != null && logEntry.size >= 2) {
                         // Parse Log Entry: SFI (1 byte), Number of Records (1 byte)
                         val sfi = logEntry[0].toInt() and 0xFF
                         val numRecords = logEntry[1].toInt() and 0xFF
                         Log.d(logTag, "Log Entry found: SFI=$sfi, Count=$numRecords")

                         if (numRecords > 0) {
                              // Read records
                              val records = readRecords(sfi, numRecords)
                              // Parse records using Log Format (if available) or standard guessing
                              transactions.addAll(parseRecords(records, logFormat))
                         }

                         // If success reading transactions, maybe break?
                         if (transactions.isNotEmpty()) break
                    } else {
                        Log.w(logTag, "No Log Entry (9F4D) found for AID ${aid.toHexString()}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error reading transactions", e)
        } finally {
             try { isoDep.close() } catch (_: Exception) {}
        }
        return transactions
    }

    private fun calculatePdolLength(pdol: ByteArray): Int {
        var len = 0
        var i = 0
        while (i < pdol.size) {
            // Tag
            i++ // Tag byte 1
            if ((pdol[i-1].toInt() and 0x1F) == 0x1F) {
                while(i < pdol.size && (pdol[i].toInt() and 0x80) != 0) i++
                i++ // Last tag byte
            }
            if (i >= pdol.size) break
            // Length
            len += pdol[i].toInt() and 0xFF
            i++
        }
        return len
    }

    private fun readRecords(sfi: Int, count: Int): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        for (i in 1..count) {
             // READ RECORD command: 00 B2 P1 P2 00
             // P2 = (SFI << 3) | 4
             val p2 = (sfi shl 3) or 4
             val cmd = byteArrayOf(0x00, 0xB2.toByte(), i.toByte(), p2.toByte(), 0x00)
             val resp = transceive(cmd)
             if (isSuccess(resp)) {
                 records.add(resp.copyOfRange(0, resp.size - 2))
             }
        }
        return records
    }

    private fun parseRecords(records: List<ByteArray>, logFormat: ByteArray?): List<CardTransaction> {
        val txs = mutableListOf<CardTransaction>()
        if (logFormat == null) {
            return emptyList()
        }

        // Parse Log Format: List of [Tag, Length]
        val formatList = parseLogFormat(logFormat)

        for (record in records) {
             try {
                 val tx = parseTransactionRecord(record, formatList)
                 if (tx != null) txs.add(tx)
             } catch (e: Exception) {
                 Log.w(logTag, "Failed to parse record", e)
             }
        }
        return txs
    }

    data class FormatItem(val tag: Int, val length: Int)

    private fun parseLogFormat(format: ByteArray): List<FormatItem> {
        val list = mutableListOf<FormatItem>()
        var i = 0
        while (i < format.size) {
            var tag = format[i].toInt() and 0xFF
            i++
            if ((tag and 0x1F) == 0x1F) {
                // 2 byte tag
                if (i < format.size) {
                    tag = (tag shl 8) or (format[i].toInt() and 0xFF)
                    i++
                }
            }
            if (i >= format.size) break
            val len = format[i].toInt() and 0xFF
            i++
            list.add(FormatItem(tag, len))
        }
        return list
    }

    private fun parseTransactionRecord(record: ByteArray, format: List<FormatItem>): CardTransaction? {
        var offset = 0
        var date = "Unknown"
        var time = ""
        var amount = "0.00"
        var currency = ""
        var type = "Unknown"

        for (item in format) {
            if (offset + item.length > record.size) break
            val value = record.copyOfRange(offset, offset + item.length)

            when (item.tag) {
                0x9A -> { // Date YYMMDD
                     val s = value.toHexString()
                     if (s.length >= 6)
                        date = "20${s.substring(0, 2)}/${s.substring(2, 4)}/${s.substring(4, 6)}"
                }
                0x9F21 -> { // Time HHMMSS
                     val s = value.toHexString()
                     if (s.length >= 6)
                        time = "${s.substring(0, 2)}:${s.substring(2, 4)}:${s.substring(4, 6)}"
                }
                0x9F02 -> { // Amount BCD
                     val s = value.toHexString()
                     try {
                         val v = s.toLong()
                         amount = String.format(java.util.Locale.US, "%.2f", v / 100.0)
                     } catch (_: Exception) {}
                }
                0x5F2A -> { // Currency
                     currency = getCurrencyCode(value)
                }
                0x9C -> { // Type
                     type = when(value[0].toInt()) {
                         0 -> "Purchase"
                         0x20 -> "Refund"
                         0x01 -> "Withdrawal"
                         else -> "Type ${value.toHexString()}"
                     }
                }
            }

            offset += item.length
        }

        if (date == "Unknown" && amount == "0.00") return null
        return CardTransaction(date, time, amount, currency, type)
    }

    private fun transceive(cmd: ByteArray): ByteArray {
        try {
            Log.d(logTag, "TX: ${cmd.toHexString()}")
            val resp = isoDep.transceive(cmd)
            Log.d(logTag, "RX: ${resp.toHexString()}")
            return resp
        } catch (e: Exception) {
            Log.e(logTag, "Transceive failed: ${e.message}")
            return ByteArray(0)
        }
    }

    private fun isSuccess(resp: ByteArray): Boolean {
        return resp.size >= 2 && resp[resp.size - 2] == 0x90.toByte() && resp[resp.size - 1] == 0x00.toByte()
    }

    private fun buildSelectApdu(aid: ByteArray): ByteArray {
        val cmd = ByteArray(5 + aid.size + 1)
        cmd[0] = 0x00
        cmd[1] = 0xA4.toByte()
        cmd[2] = 0x04
        cmd[3] = 0x00
        cmd[4] = aid.size.toByte()
        System.arraycopy(aid, 0, cmd, 5, aid.size)
        cmd[cmd.size-1] = 0x00
        return cmd
    }

    private fun findTlv(data: ByteArray, tag: Int): ByteArray? {
        var i = 0
        while (i < data.size - 2) {
            // handle padding
            if (data[i] == 0x00.toByte() || data[i] == 0xFF.toByte()) { i++; continue }

            // Read Tag
            var t = data[i].toInt() and 0xFF
            val isConstructed = (t and 0x20) != 0
            i++

            // Multibyte tag check (if bits 1-5 become 11111)
            if ((t and 0x1F) == 0x1F) {
                while (i < data.size && (data[i].toInt() and 0x80) != 0) {
                     t = (t shl 8) or (data[i].toInt() and 0xFF)
                     i++
                }
                if (i < data.size) {
                    t = (t shl 8) or (data[i].toInt() and 0xFF)
                    i++
                }
            }

            // Length
            if (i >= data.size) break
            var l = data[i].toInt() and 0xFF
            i++
            if ((l and 0x80) != 0) {
                val n = l and 0x7F
                l = 0
                repeat(n) {
                   if (i >= data.size) return@repeat
                   l = (l shl 8) or (data[i].toInt() and 0xFF)
                   i++
                }
            }

            // If length is huge or invalid, break
            if (i + l > data.size) break

            // Check match
            if (t == tag) {
                return data.copyOfRange(i, i + l)
            }

            // Search inside constructed
            if (isConstructed) {
                val child = findTlv(data.copyOfRange(i, i + l), tag)
                if (child != null) return child
            }

            // Move to next TLV
            i += l
        }
        return null
    }

    private fun extractAids(ppseData: ByteArray): List<ByteArray> {
        val aids = mutableListOf<ByteArray>()
        // 4F is AID tag. It appears inside 61 (Directory Entry).
        // Since we have findTlv, we can try to find all 61, then inside find 4F.
        // But findTlv returns only the first match.
        // Simple scan for 4F within PPSE data:
        var i = 0
        while (i < ppseData.size - 2) {
             if (ppseData[i] == 0x4F.toByte()) {
                 // Check length
                 val len = ppseData[i+1].toInt() and 0xFF
                 if (len in 5..16 && i+2+len <= ppseData.size) {
                      aids.add(ppseData.copyOfRange(i+2, i+2+len))
                 }
            }
            i++
        }
        return aids.distinctBy { it.toHexString() }
    }

    private fun getCurrencyCode(data: ByteArray): String {
        return when(data.toHexString()) {
             "0156" -> "CNY"
             "0840" -> "USD"
             "0978" -> "EUR"
             "0392" -> "JPY"
             "0826" -> "GBP"
             else -> data.toHexString()
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }
}

