import com.zulutek.preview.*

private class FakeLink(vararg incoming: ByteArray) : ZtfLink {
    private val replies = ArrayDeque(incoming.toList())
    val sent = mutableListOf<ByteArray>()
    override fun send(pkt: ByteArray): Boolean { sent.add(pkt); return true }
    override fun read(timeoutMs: Long): ByteArray? = replies.removeFirstOrNull()
    override fun close() {}
}
private fun int(value: Int) = ByteArray(4).also { ZtfCodec.put32(it, 0, value) }
private fun file(op: Int, body: ByteArray = byteArrayOf()) = ZtfCodec.frame(
    ZtfCodec.CMD_FILES, byteArrayOf(op.toByte(), ZtfCodec.ST_OK.toByte(), 0, 0) + body
)
fun main() {
    val changed = ZtfCodec.frame(ZtfCodec.CMD_PARAMS,
        byteArrayOf(ZtfCodec.PQ_CHANGED.toByte(), 0, 1, 42, 0) + int(1234))
    val fake = FakeLink(changed, ZtfCodec.ztfFrame('G', byteArrayOf(9)),
        ZtfCodec.ztfFrame('L', byteArrayOf(7, 0)), ZtfCodec.ztfFrame('L', byteArrayOf(2, 0)),
        ZtfCodec.ztfFrame('L', byteArrayOf(2, 1)))
    val ztf = ZtfTransport(fake)
    check(ztf.ztfLoad(2))
    check(ztf.pollNotes() == listOf(42 to 1234))
    check(!ztf.ztfLoad(2))
    check(fake.sent.size == 2)
    println("ZTF: wrong command/index ignored, notifications preserved, LOAD success/failure passed")

    val payload = byteArrayOf(10, 20, 30, 40)
    val sum = int(payload.size) + int(ZtfCodec.crc32(payload).toInt())
    val read = ZtfTransport(FakeLink(file(ZtfCodec.OP_SUM, sum),
        file(ZtfCodec.OP_READ, int(0) + payload)))
    check(read.fileRead(0).contentEquals(payload))
    val stalledRead = ZtfTransport(FakeLink(file(ZtfCodec.OP_SUM, sum), file(ZtfCodec.OP_READ, int(0))))
    check(runCatching { stalledRead.fileRead(0) }.exceptionOrNull()?.message?.contains("progression") == true)
    val badCrc = ZtfTransport(FakeLink(file(ZtfCodec.OP_SUM, sum), file(ZtfCodec.OP_READ, int(0) + ByteArray(4))))
    check(runCatching { badCrc.fileRead(0) }.exceptionOrNull()?.message?.contains("CRC") == true)
    println("ZTF: read, stalled read, bad CRC passed")

    val write = FakeLink(file(ZtfCodec.OP_BEGIN, int(2)), file(ZtfCodec.OP_DATA, int(4)), file(ZtfCodec.OP_COMMIT))
    check(ZtfTransport(write).fileWrite(0, payload, 2))
    check(write.sent.size == 3)
    val data = ZtfCodec.parseFrame(write.sent[1])!!.second
    check(data.copyOfRange(data.size - 2, data.size).contentEquals(payload.copyOfRange(2, 4)))
    val stalledWrite = ZtfTransport(FakeLink(file(ZtfCodec.OP_BEGIN, int(0)), file(ZtfCodec.OP_DATA, int(0))))
    check(runCatching { stalledWrite.fileWrite(0, payload) }.exceptionOrNull()?.message?.contains("progression") == true)
    val empty = FakeLink()
    check(runCatching { ZtfTransport(empty).fileWrite(0, payload, 0) }.exceptionOrNull() is IllegalArgumentException)
    check(empty.sent.isEmpty())
    println("ZTF: resumed write, stalled write, zero chunk passed")
}
