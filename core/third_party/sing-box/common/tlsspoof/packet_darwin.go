package tlsspoof

import "net/netip"

func buildSpoofTCPSegment(method Method, src, dst netip.AddrPort, sendNext, receiveNext, timestamp uint32, payload []byte) ([]byte, error) {
	packetInfo, err := resolveSpoofPacketInfo(method, sendNext, receiveNext, timestamp, nil, payload)
	if err != nil {
		return nil, err
	}
	segment := make([]byte, tcpHeaderLen+len(packetInfo.options)+len(payload))
	encodeTCP(segment, 0, src, dst, packetInfo, payload)
	return segment, nil
}
