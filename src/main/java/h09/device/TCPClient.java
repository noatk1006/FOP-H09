package h09.device;

import h09.connection.Connection;
import h09.exceptions.InternetException;
import h09.exceptions.packet.PacketException;
import h09.packet.PacketType;
import h09.utils.TCPUtils;
import h09.packet.Packet;
import org.tudalgo.algoutils.student.annotation.DoNotTouch;
import org.tudalgo.algoutils.student.annotation.SolutionOnly;
import org.tudalgo.algoutils.student.annotation.StudentImplementationRequired;

import static h09.packet.PacketType.*;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A TCP client implementation that extends the abstract {@link Client} class.
 * Implements TCP-specific functionality for establishing connections,
 * sending and receiving data with proper sequencing and acknowledgment.
 */
@DoNotTouch
public class TCPClient extends Client {

    /**
     * The current sequence number for TCP communication.
     */
    private int sequence;

    public TCPClient(int serverPort) throws InternetException {
        super(serverPort);
        sequence = ThreadLocalRandom.current().nextInt(1, 1000);
    }

    /**
     * Connects to the remote server by sending a SYN packet and awaiting
     * a response SYN packet from the server. Also validates the returned
     * packet.
     *
     * @throws InternetException If there is an error with the network connection
     * @throws PacketException   If there is an error with packet handling
     */
    @Override
    @StudentImplementationRequired("H9.4.1")
    public void connect() throws InternetException, PacketException {
        Connection conn = getConn();
        int sendSequence = sequence;

        // 1 Send SYN and wait receive SYN and sequence ++
        Packet received = TCPUtils.try3Times(() ->{
            conn.sendPacket(sendSequence,SYN,null);
            return conn.waitForPacketTimeout(5000);
        },sendSequence +1);

        //2 Validate the received packet from the server
        received.expectType(SYN);
        received.expectSequenceNumber(sendSequence + 1);
        received.validateChecksum();

        //3
        sequence = sendSequence+2;
    }

    /**
     * Sends a {@link String} of variable, unbounded length to the remote server. Packs the
     * data into one or more DATA packets if necessary. For each DATA packet
     * also awaits the acknowledgement packet and validates it.
     * <p>
     * After all data is sent, a DATA packet with content {@code "<EOF>"} is sent
     * to signal the end of the client's data. The response ACK is also validated.
     *
     * @param data The data to send
     * @throws InternetException        If there is an error with the network connection
     * @throws PacketException          If there is an error with packet handling
     * @throws IllegalArgumentException if data is null
     */
    @Override
    @StudentImplementationRequired("H9.4.2")
    public void send(String data) throws InternetException, PacketException {
        //must not be null
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        //divide chunk
        Connection conn = getConn();
        String remaining = data;
        while (!remaining.isEmpty()) {
            int chunkSize = Math.min(8, remaining.length());
            String chunk = remaining.substring(0, chunkSize);
            remaining = remaining.substring(chunkSize);
            //send
            sendDataChunk(chunk);
        }
        sendDataChunk("<EOF>");
    }
    //send data chunk
    private void sendDataChunk(String chunk) throws InternetException, PacketException{
        int currentSequence = sequence;
        int expectedACKSequence = sequence+1+chunk.length();
        Packet ACK = TCPUtils.try3Times(() ->{
            getConn().sendPacket(currentSequence,DATA,chunk);
            return getConn().waitForPacketTimeout(5000);
        },expectedACKSequence);

        //check
        ACK.expectType(PacketType.ACK);
        ACK.expectSequenceNumber(expectedACKSequence);
        ACK.validateChecksum();

        sequence = expectedACKSequence;
    }
    /**
     * Retrieves the message from the remote server, combining multiple
     * DATA packets if necessary.
     * <p>
     * First sends an ACK to signal that the server can now send data.
     * Then awaits a DATA packet from the server, validates it and responds
     * with an acknowledgement. Repeats receiving and acknowledging data
     * until the server sends a DATA packet with {@code "<EOF>"}.
     * The EOF packet does not need to be acknowledged.
     *
     * @return the complete message from the server
     * @throws InternetException If there is an error with the network connection
     * @throws PacketException   If there is an error with packet handling
     */
    @Override
    @StudentImplementationRequired("H9.4.3")
    public String receive() throws InternetException, PacketException {
        Connection conn = getConn();
        StringBuilder stringBuilder = new StringBuilder();
        int ackSequence = sequence;

        while (true){
            int currentACKSequence = ackSequence;
            int expectedACKSequence = currentACKSequence+1;
            Packet received = TCPUtils.try3Times(()->{
                conn.sendPacket(currentACKSequence,ACK,null);
                return getConn().waitForPacketTimeout(5000);
            },expectedACKSequence);

            sequence =  currentACKSequence+2;

            received.expectType(DATA);
            received.expectSequenceNumber(expectedACKSequence);
            received.validateChecksum();

            String chunk = received.getData();
            if(chunk.equals("<EOF>")){
                sequence = expectedACKSequence+1+chunk.length();
                return stringBuilder.toString();
            }
            stringBuilder.append(chunk);
            ackSequence = expectedACKSequence+1+chunk.length();
        }


    }

    /**
     * Closes the connection. Sends a CLOSE packet to the
     * server with a sequence number of {@code Integer.MAX_VALUE}.
     * If the connection is already closed or another error occurs
     * while sending the packet, the error is caught and ignored.
     * At the end calls close on the superclass.
     */
    @Override
    @StudentImplementationRequired("H9.4.4")
    public void close() {
        try {
            Connection conn = getConn();
            conn.sendPacket(Integer.MAX_VALUE,CLOSE,null);
        }
        catch (Exception e) {}
        super.close();
    }
}
