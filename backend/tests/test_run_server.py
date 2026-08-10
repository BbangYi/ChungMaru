import socket
import sys
import unittest
from pathlib import Path


API_DIR = Path(__file__).resolve().parents[1] / "api"
if str(API_DIR) not in sys.path:
    sys.path.insert(0, str(API_DIR))


from run_server import create_listener  # noqa: E402


class RunServerTest(unittest.TestCase):
    def test_listener_accepts_ipv4_when_dual_stack_is_available(self):
        listener = create_listener(0)
        try:
            if socket.has_dualstack_ipv6():
                self.assertEqual(socket.AF_INET6, listener.family)
                self.assertEqual(
                    0,
                    listener.getsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY),
                )
            else:
                self.assertEqual(socket.AF_INET, listener.family)
        finally:
            listener.close()


if __name__ == "__main__":
    unittest.main()
