import base64
import bdb
import pdb
import socket
import sys
import traceback


def encoded(value):
    return base64.b64encode(value.encode('utf-8', errors='replace')).decode('ascii')


class ZidePdb(pdb.Pdb):
    def __init__(self, channel):
        super().__init__(readrc=False, nosigint=True)
        self.channel = channel

    def interaction(self, frame, tb):
        if frame is None and tb is not None:
            while tb.tb_next:
                tb = tb.tb_next
            frame = tb.tb_frame
        if frame is None:
            self.set_quit()
            return
        self.channel.write('STOP\t{}\t{}\n'.format(frame.f_lineno, encoded(frame.f_code.co_filename)))
        values = dict(frame.f_globals)
        values.update(frame.f_locals)
        for name, value in sorted(values.items()):
            if name.startswith('__'):
                continue
            try:
                rendered = repr(value)[:4096]
            except Exception:
                rendered = '<unavailable>'
            fields = [name, type(value).__name__, frame.f_code.co_name, rendered]
            self.channel.write('VAR\t' + '\t'.join(encoded(v) for v in fields) + '\n')
        self.channel.write('READY\n')
        self.channel.flush()
        command = self.channel.readline().strip()
        if command == 'continue':
            self.set_continue()
        elif command == 'next':
            self.set_next(frame)
        else:
            self.set_quit()


source, port, breakpoints = sys.argv[1:4]
sys.argv = [source]
sys.path.insert(0, __import__('os').getcwd())
with socket.create_connection(('127.0.0.1', int(port))) as connection:
    with connection.makefile('rw', encoding='utf-8', newline='\n') as channel:
        debugger = ZidePdb(channel)
        for line in set(filter(None, breakpoints.split(','))):
            error = debugger.set_break(source, int(line))
            if error:
                print(error, file=sys.stderr)
        namespace = {'__name__': '__main__', '__file__': source, '__builtins__': __builtins__}
        try:
            with open(source, encoding='utf-8') as script:
                code = compile(script.read(), source, 'exec')
            debugger.run(code, namespace, namespace)
        except bdb.BdbQuit:
            pass
        except SystemExit:
            raise
        except BaseException:
            traceback.print_exc()
