package jamiebalfour.zide.editor;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/** Small JDI-backed debugger for Java source launched by ZIDE. */
final class JavaDebugSession implements AutoCloseable {
  record Variable(String name, String type, String function, String value) { }
  record Pause(int line, String file, List<Variable> variables) { }

  private final Path classpath;
  private final String mainClass;
  private final Set<Integer> breakpointLines;
  private final Consumer<Pause> onPause;
  private final Consumer<String> onOutput;
  private final Consumer<Exception> onError;
  private final Runnable onFinished;
  private final BlockingQueue<String> commands = new LinkedBlockingQueue<>();
  private volatile VirtualMachine vm;
  private volatile boolean closed;

  JavaDebugSession(Path classpath, String mainClass, Set<Integer> breakpointLines,
                   Consumer<Pause> onPause, Consumer<String> onOutput, Consumer<Exception> onError, Runnable onFinished) {
    this.classpath = classpath;
    this.mainClass = mainClass;
    this.breakpointLines = Set.copyOf(breakpointLines);
    this.onPause = onPause;
    this.onOutput = onOutput;
    this.onError = onError;
    this.onFinished = onFinished;
  }

  void start() throws Exception {
    var connector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, com.sun.jdi.connect.Connector.Argument> arguments = connector.defaultArguments();
    arguments.get("main").setValue(mainClass);
    arguments.get("options").setValue("-cp \"" + classpath.toAbsolutePath() + "\"");
    vm = connector.launch(arguments);
    pump(vm.process().getInputStream());
    pump(vm.process().getErrorStream());
    Thread debugger = new Thread(this::eventLoop, "zide-java-debugger");
    debugger.setDaemon(true);
    debugger.start();
  }

  private void pump(InputStream stream) {
    Thread output = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        for (String line; (line = reader.readLine()) != null;) onOutput.accept(line + "\n");
      } catch (IOException ignored) { }
    }, "zide-java-debug-output");
    output.setDaemon(true);
    output.start();
  }

  private void eventLoop() {
    try {
      EventRequestManager requests = vm.eventRequestManager();
      ClassPrepareRequest prepare = requests.createClassPrepareRequest();
      prepare.addClassFilter(mainClass);
      prepare.enable();
      EventQueue queue = vm.eventQueue();
      while (!closed) {
        EventSet set = queue.remove();
        boolean resume = true;
        for (Event event : set) {
          if (event instanceof ClassPrepareEvent prepared) {
            installBreakpoints(requests, prepared.referenceType());
          } else if (event instanceof BreakpointEvent breakpoint) {
            resume = false;
            handlePause(breakpoint.thread(), breakpoint.location().lineNumber(), breakpoint.location().sourceName());
          } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
            closed = true;
          }
        }
        if (resume) set.resume();
      }
    } catch (Exception failure) {
      if (!closed) onError.accept(failure);
    } finally {
      onFinished.run();
    }
  }

  private void installBreakpoints(EventRequestManager requests, ReferenceType type) {
    for (int line : breakpointLines) {
      try {
        for (var location : type.locationsOfLine(line)) {
          BreakpointRequest request = requests.createBreakpointRequest(location);
          request.enable();
        }
      } catch (Exception ignored) { }
    }
  }

  private void handlePause(ThreadReference thread, int line, String file) throws Exception {
    List<Variable> values = new ArrayList<>();
    if (thread.frameCount() > 0) {
      StackFrame frame = thread.frame(0);
      String function = frame.location().method().name();
      try {
        for (LocalVariable variable : frame.visibleVariables()) {
          Value value = frame.getValue(variable);
          values.add(new Variable(variable.name(), variable.typeName(), function, String.valueOf(value)));
        }
      } catch (com.sun.jdi.AbsentInformationException ignored) { }
    }
    onPause.accept(new Pause(line, file, List.copyOf(values)));
    String command = commands.take();
    if ("stop".equals(command)) {
      close();
      return;
    }
    if ("step".equals(command)) {
      EventRequestManager requests = vm.eventRequestManager();
      StepRequest step = requests.createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
      step.addCountFilter(1);
      step.enable();
    }
  }

  void resume(boolean step) { commands.offer(step ? "step" : "continue"); }
  void stop() { commands.offer("stop"); }

  @Override public void close() {
    closed = true;
    commands.offer("stop");
    VirtualMachine current = vm;
    if (current != null) {
      try { current.dispose(); } catch (Exception ignored) { }
    }
  }
}
