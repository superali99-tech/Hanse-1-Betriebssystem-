from novaos.common import wifi

LIST = """\
*:Zuhause\\:5G:82:WPA2
 :Zuhause\\:5G:40:WPA2
 :Nachbar:55:WPA1 WPA2
 ::70:WPA2
 :Cafe Gast:30:
 :Telekom:0:--
"""


def test_terse_split_keeps_escaped_colons():
    assert wifi.split_terse(r"*:Zuhause\:5G:82:WPA2") == ["*", "Zuhause:5G", "82", "WPA2"]


def test_networks_deduplicated_sorted_hidden_left_out():
    nets = wifi.parse_networks(LIST)
    assert [n.ssid for n in nets] == ["Zuhause:5G", "Nachbar", "Cafe Gast", "Telekom"]
    home = nets[0]
    assert home.in_use and home.secured and home.signal == 82 and home.bars == 4
    assert not nets[2].secured and not nets[3].secured
    assert nets[3].bars == 0 and nets[2].bars == 2


def test_connectivity_and_devices():
    assert wifi.parse_connectivity("full\n") == "full"
    assert wifi.parse_connectivity("") == "unknown"
    assert wifi.parse_wifi_devices("wlp2s0:wifi\nenp3s0:ethernet\nlo:loopback\n") == ["wlp2s0"]


def test_error_reasons():
    assert wifi.error_message("Error: Connection activation failed: Secrets were required, but not provided.") == "password"
    assert wifi.error_message("Error: 802-11-wireless-security.psk: property is invalid.") == "password"
    assert wifi.error_message("Error: No network with SSID 'X' found.") == "not_found"
    assert wifi.error_message("Error: something else") == "something else"
