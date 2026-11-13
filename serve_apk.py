#!/usr/bin/env python3
"""Serves the debug APK and accepts POST /frame PNGs into ./frames for offline testing."""
import http.server
import os
import time

BASE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(BASE, "app/build/outputs/apk/debug")
FRAMES = os.path.join(BASE, "frames")


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def do_POST(self):
        if not self.path.startswith("/frame"):
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0 or length > 20_000_000:
            self.send_error(400)
            return
        data = self.rfile.read(length)
        os.makedirs(FRAMES, exist_ok=True)
        name = time.strftime("frame-%Y%m%d-%H%M%S.png")
        with open(os.path.join(FRAMES, name), "wb") as handle:
            handle.write(data)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")
        print("saved", name, length, flush=True)

    def log_message(self, *args):
        pass


with http.server.ThreadingHTTPServer(("0.0.0.0", 8099), Handler) as server:
    print("serving APK + POST /frame on :8099", flush=True)
    server.serve_forever()
