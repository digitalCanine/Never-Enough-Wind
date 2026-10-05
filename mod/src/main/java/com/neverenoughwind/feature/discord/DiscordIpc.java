package com.neverenoughwind.feature.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// talks to the discord app running on this computer, over the local pipe discord opens for games.
// nothing here goes over the network, discord does that part itself
final class DiscordIpc implements Closeable {
    private static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2;

    private interface Pipe extends Closeable {
        void write(ByteBuffer data) throws IOException;

        void read(ByteBuffer into) throws IOException;
    }

    private final Pipe pipe;

    private DiscordIpc(Pipe pipe) {
        this.pipe = pipe;
    }

    // null when discord isnt running or doesnt answer
    static DiscordIpc connect(String applicationId) {
        for (int i = 0; i < 10; i++) {
            Pipe pipe = open(i);
            if (pipe == null) continue;
            DiscordIpc ipc = new DiscordIpc(pipe);
            try {
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", applicationId);
                ipc.send(HANDSHAKE, hello);
                JsonObject answer = ipc.receive();
                if (answer != null && "READY".equals(text(answer, "evt"))) return ipc;
            } catch (IOException | RuntimeException e) {
                // try the next pipe
            }
            ipc.close();
        }
        return null;
    }

    private static Pipe open(int index) {
        String name = "discord-ipc-" + index;
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                RandomAccessFile file = new RandomAccessFile("\\\\?\\pipe\\" + name, "rw");
                return new Pipe() {
                    @Override
                    public void write(ByteBuffer data) throws IOException {
                        file.write(data.array(), data.position(), data.remaining());
                    }

                    @Override
                    public void read(ByteBuffer into) throws IOException {
                        file.readFully(into.array(), into.position(), into.remaining());
                    }

                    @Override
                    public void close() throws IOException {
                        file.close();
                    }
                };
            }
            for (Path dir : unixDirs()) {
                Path socket = dir.resolve(name);
                if (!Files.exists(socket)) continue;
                SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                try {
                    channel.connect(UnixDomainSocketAddress.of(socket));
                } catch (IOException e) {
                    channel.close();
                    continue;
                }
                return new Pipe() {
                    @Override
                    public void write(ByteBuffer data) throws IOException {
                        while (data.hasRemaining()) channel.write(data);
                    }

                    @Override
                    public void read(ByteBuffer into) throws IOException {
                        while (into.hasRemaining()) {
                            if (channel.read(into) < 0) throw new EOFException();
                        }
                    }

                    @Override
                    public void close() throws IOException {
                        channel.close();
                    }
                };
            }
        } catch (IOException | RuntimeException e) {
            // no pipe with this number
        }
        return null;
    }

    // where discord puts its socket on linux and mac, including the flatpak and snap builds
    private static List<Path> unixDirs() {
        List<Path> out = new ArrayList<>();
        for (String var : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String value = System.getenv(var);
            if (value == null || value.isBlank()) continue;
            Path dir = Path.of(value);
            out.add(dir);
            out.add(dir.resolve("app/com.discordapp.Discord"));
            out.add(dir.resolve("snap.discord"));
        }
        out.add(Path.of("/tmp"));
        return out;
    }

    // activity = null clears the presence
    void setActivity(JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        send(FRAME, command);
        // discord answers every command, read it so the pipe doesnt fill up
        receive();
    }

    private void send(int op, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(op).putInt(body.length).put(body).flip();
        pipe.write(frame);
    }

    private JsonObject receive() throws IOException {
        ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        pipe.read(head);
        head.flip();
        int op = head.getInt(), length = head.getInt();
        if (length < 0 || length > 1 << 20) throw new IOException("bad frame");
        ByteBuffer body = ByteBuffer.allocate(length);
        pipe.read(body);
        if (op == CLOSE) throw new IOException("discord closed the pipe");
        return JsonParser.parseString(new String(body.array(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String text(JsonObject o, String field) {
        return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsString() : null;
    }

    @Override
    public void close() {
        try {
            pipe.close();
        } catch (IOException e) {
            // already gone
        }
    }
}
