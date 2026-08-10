"""Run the ChungMaru API on one IPv4/IPv6 listener."""

from __future__ import annotations

import argparse
import os
import socket

import uvicorn


def create_listener(port: int) -> socket.socket:
    if socket.has_dualstack_ipv6():
        return socket.create_server(
            ("::", port),
            family=socket.AF_INET6,
            dualstack_ipv6=True,
        )
    return socket.create_server(("0.0.0.0", port), family=socket.AF_INET)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    if not 1 <= args.port <= 65_535:
        parser.error("--port must be between 1 and 65535")

    os.environ["CHUNGMARU_ANALYSIS_PORT"] = str(args.port)
    listener = create_listener(args.port)
    config = uvicorn.Config("app:app", host="::", port=args.port)
    uvicorn.Server(config).run(sockets=[listener])


if __name__ == "__main__":
    main()
