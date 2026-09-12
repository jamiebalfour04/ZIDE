package jamiebalfour.zide.editor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Carries debugger commands separately from the Python program's console. */
final class PythonDebugSession implements AutoCloseable {
  record Variable(String name, String type, String function, String value) { }
  record Pause(int line, String file, List<Variable> variables) { }
  private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
  private volatile Socket socket;
  private volatile PrintWriter commands;
  private volatile boolean paused;
  private volatile boolean closed;

  PythonDebugSession(Consumer<Pause> onPause, Consumer<Exception> onError) throws IOException {
    server.setSoTimeout(30000);
    Thread reader = new Thread(() -> {
      try (Socket connection = server.accept()) {
        socket = connection;
        if (closed) return;
        commands = new PrintWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        int line = 0;
        String file = "";
        List<Variable> values = new ArrayList<>();
        for (String message; (message = input.readLine()) != null;) {
          String[] fields = message.split("\t", -1);
          if (fields[0].equals("STOP")) {
            line = Integer.parseInt(fields[1]); file = decode(fields[2]); values.clear();
          } else if (fields[0].equals("VAR")) {
            values.add(new Variable(decode(fields[1]), decode(fields[2]), decode(fields[3]), decode(fields[4])));
          } else if (fields[0].equals("READY")) {
            paused = true;
            onPause.accept(new Pause(line, file, List.copyOf(values)));
          }
        }
      } catch (Exception failure) {
        if (!closed) onError.accept(failure);
      }
    }, "zide-python-debugger");
    reader.setDaemon(true); reader.start();
  }

  int port() { return server.getLocalPort(); }
  synchronized boolean resume(boolean step) {
    if (!paused || commands == null || closed) return false;
    paused = false;
    commands.println(step ? "next" : "continue");
    return true;
  }
  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }
  public void close() {
    closed = true; paused = false;
    try { server.close(); } catch (IOException ignored) { }
    try { if (socket != null) socket.close(); } catch (IOException ignored) { }
  }
}
