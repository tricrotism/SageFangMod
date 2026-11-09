"""Repeatable frame-time benchmark for the dev client, driven over RemoteControl.

Joins a server, turns on a module set, pans the camera at a fixed rate, and records frame times with
RemoteControl's `perf` verb. Each run appends one JSON line to the results file, so any change can be
judged against the runs before it.

    python tools/bench.py run --label baseline --modules none
    python tools/bench.py run --label esp --modules suschunks,blockentitychunkesp,chunkdebug
    python tools/bench.py compare baseline esp

The client needs `remote.port` set, plus these in run/options.txt or the numbers measure something
else: enableVsync:false, maxFps:260, pauseOnLostFocus:false, inactivityFpsLimit:"minimized".
A focused-window check is impossible from here, and a paused or FPS-capped client still answers.
"""
import argparse
import json
import socket
import statistics
import sys
import time

FIELDS = ("fps", "p50", "p95", "p99", "p999", "max", "allocMBs")


class Remote:
    def __init__(self, port):
        self.sock = socket.create_connection(("127.0.0.1", port), 15)
        self.io = self.sock.makefile("rw", encoding="utf-8")

    def __call__(self, command):
        self.io.write(command + "\n")
        self.io.flush()
        reply = self.io.readline().strip()
        if reply.startswith("error"):
            raise RuntimeError(f"{command!r} -> {reply}")
        return reply

    def wait_for(self, command, done, timeout):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            reply = self(command)
            if done(reply):
                return reply
            time.sleep(0.5)
        raise TimeoutError(f"{command!r} never finished")


def parse(report):
    values = dict(pair.split("=", 1) for pair in report.split())
    return {k: float(values[k]) for k in FIELDS} | {"frames": int(values["frames"]), "gc": values["gc"]}


def run(args):
    rc = Remote(args.port)
    if rc("screen") == "menu":
        rc(f"connect {args.server}")
        rc.wait_for("screen", lambda r: r == "world", 60)
        time.sleep(args.settle)

    modules = [] if args.modules == "none" else args.modules.split(",")
    before = dict(pair.split("=") for pair in rc("modules").split())
    for module in modules:
        rc(f"module {module} on")

    try:
        results = []
        for i in range(args.runs):
            rc("look 0 0")
            rc(f"sweep {args.pan} 0 100000")
            time.sleep(args.warmup)
            rc(f"perf {args.frames}")
            report = rc.wait_for("perf", lambda r: not r.startswith("recording"), args.frames / 10 + 120)
            rc("stop")
            results.append(parse(report))
            print(f"  run {i + 1}: " + " ".join(f"{k}={results[-1][k]}" for k in FIELDS))
    finally:
        rc("stop")
        for module in modules:
            if before.get(module) == "off":
                rc(f"module {module} off")

    summary = {k: statistics.median(r[k] for r in results) for k in FIELDS}
    line = {"label": args.label, "at": time.strftime("%Y-%m-%dT%H:%M:%S"), "modules": modules,
            "frames": args.frames, "runs": results, "median": summary, "note": args.note}
    with open(args.out, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(line) + "\n")
    print(f"{args.label} median: " + " ".join(f"{k}={summary[k]:.2f}" for k in FIELDS))


def compare(args):
    latest = {}
    with open(args.out, encoding="utf-8") as fh:
        for raw in fh:
            entry = json.loads(raw)
            latest[entry["label"]] = entry["median"]
    a, b = latest[args.before], latest[args.after]
    print(f"{'metric':<10}{args.before:>12}{args.after:>12}{'change':>10}")
    for k in FIELDS:
        delta = (b[k] - a[k]) / a[k] * 100 if a[k] else 0.0
        print(f"{k:<10}{a[k]:>12.2f}{b[k]:>12.2f}{delta:>+9.1f}%")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=25580, help="RemoteControl port (remote.port)")
    parser.add_argument("--out", default="run/bench.jsonl", help="results file, one JSON line per run")
    sub = parser.add_subparsers(dest="cmd", required=True)

    r = sub.add_parser("run")
    r.add_argument("--label", required=True)
    r.add_argument("--modules", default="none", help="comma-separated module ids, or none")
    r.add_argument("--server", default="127.0.0.1:25590")
    r.add_argument("--frames", type=int, default=3000)
    r.add_argument("--runs", type=int, default=3)
    r.add_argument("--warmup", type=float, default=10, help="seconds of panning before each recording")
    r.add_argument("--settle", type=float, default=15, help="seconds after joining, for chunks to load")
    r.add_argument("--pan", type=float, default=2, help="camera yaw degrees per tick")
    r.add_argument("--note", default="")
    r.set_defaults(func=run)

    c = sub.add_parser("compare")
    c.add_argument("before")
    c.add_argument("after")
    c.set_defaults(func=compare)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    sys.exit(main())
