import com.zulutek.preview.ZuluLink
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

fun main() {
    DatagramSocket(ZuluLink.DIRECT_PORT).use {
        check(runCatching { ZuluLink.start("test-console", ZuluLink.Role.CONSOLE) }
            .exceptionOrNull() is SocketException)
    }

    val incoming = LinkedBlockingQueue<ZuluLink.RxMessage>()
    ZuluLink.onMessage = { incoming.offer(it) }
    // Use the app's packet encoder to test its UDP send/receive contract.
    val encodeCc = ZuluLink.javaClass.getDeclaredMethod(
        "encodeCc", String::class.java, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
    ).apply { isAccessible = true }
    repeat(2) {
        ZuluLink.start("test-console", ZuluLink.Role.CONSOLE)
        try {
            DatagramSocket().use { socket ->
                fun send(target: String, invalidate: Boolean = false) {
                    val packet = encodeCc.invoke(ZuluLink, target, 78, 0, 93) as ByteArray
                    if (invalidate) packet[0] = 0
                    socket.send(DatagramPacket(packet, packet.size, InetAddress.getLoopbackAddress(), ZuluLink.DIRECT_PORT))
                }
                send("other")
                send("test-console", invalidate = true)
                send("TOUS")
                val message = checkNotNull(incoming.poll(3, TimeUnit.SECONDS))
                check(message.type == ZuluLink.T_CC && message.cc == 78 && message.value == 93)
                check(incoming.poll(150, TimeUnit.MILLISECONDS) == null)
            }
        } finally {
            ZuluLink.stop()
        }
    }
    println("ZuluLink: port conflict, restart, target filtering, invalid frame and all-peer CC passed")
}
