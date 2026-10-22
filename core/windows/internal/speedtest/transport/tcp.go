package transport

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"net"
	"strconv"
	"time"
)

var (
	pingPrefix = []byte{0x50, 0x49, 0x4e, 0x47, 0x20}

	initPacket = []byte{0x49, 0x4e, 0x49, 0x54, 0x50, 0x4c, 0x4f, 0x53, 0x53}
	packetLoss = []byte{0x50, 0x4c, 0x4f, 0x53, 0x53}
	hiFormat   = []byte{0x48, 0x49}
	quitFormat = []byte{0x51, 0x55, 0x49, 0x54}
)

var (
	ErrEchoData                    = errors.New("incorrect echo data")
	ErrEmptyConn                   = errors.New("empty conn")
	ErrUnsupported                 = errors.New("unsupported protocol")
	ErrUninitializedPacketLossInst = errors.New("uninitialized packet loss inst")
)

func pingFormat(locTime int64) []byte {
	return strconv.AppendInt(pingPrefix, locTime, 10)
}

type Client struct {
	id      string
	conn    net.Conn
	host    string
	version string

	dialFunc func(ctx context.Context, network string, address string) (net.Conn, error)

	reader *bufio.Reader
}

func NewClient(dialFunc func(ctx context.Context, network string, address string) (net.Conn, error)) (*Client, error) {
	uuid, err := generateUUID()
	if err != nil {
		return nil, err
	}
	return &Client{
		id:       uuid,
		dialFunc: dialFunc,
	}, nil
}

func (client *Client) ID() string {
	return client.id
}

func (client *Client) Connect(ctx context.Context, host string) (err error) {
	client.host = host
	client.conn, err = client.dialFunc(ctx, "tcp", client.host)
	if err != nil {
		return err
	}
	client.reader = bufio.NewReader(client.conn)
	return nil
}

func (client *Client) Disconnect() (err error) {
	_, _ = client.conn.Write(quitFormat)
	client.conn = nil
	client.reader = nil
	client.version = ""
	return
}

func (client *Client) Write(data []byte) (err error) {
	if client.conn == nil {
		return ErrEmptyConn
	}
	data = append(data, '\n')
	_, err = client.conn.Write(data)
	return
}

func (client *Client) Read() ([]byte, error) {
	if client.conn == nil {
		return nil, ErrEmptyConn
	}
	return client.reader.ReadBytes('\n')
}

func (client *Client) Version() string {
	if len(client.version) == 0 {
		err := client.Write(hiFormat)
		if err == nil {
			message, err := client.Read()
			if err != nil || len(message) < 8 {
				return "unknown"
			}
			client.version = string(message[6 : len(message)-1])
		}
	}
	return client.version
}

func (client *Client) PingContext(ctx context.Context) (int64, error) {
	resultChan := make(chan error, 1)

	var accumulatedLatency int64 = 0
	var firstReceivedByServer int64

	go func() {
		for i := 0; i < 2; i++ {
			t0 := time.Now().UnixNano()
			if err := client.Write(pingFormat(t0)); err != nil {
				resultChan <- err
				return
			}
			data, err := client.Read()
			t2 := time.Now().UnixNano()
			if err != nil {
				resultChan <- err
				return
			}
			if len(data) != 19 {
				resultChan <- ErrEchoData
				return
			}
			tx, err := strconv.ParseInt(string(data[5:18]), 10, 64)
			if err != nil {
				resultChan <- err
				return
			}
			accumulatedLatency += (t2 - t0) * 4 / 10
			if i == 0 {
				firstReceivedByServer = tx
			} else {
				accumulatedLatency += (tx - firstReceivedByServer) * 1000 * 1000 * 2 / 10
			}
		}
		resultChan <- nil
		close(resultChan)
	}()

	select {
	case err := <-resultChan:
		return accumulatedLatency, err
	case <-ctx.Done():
		return 0, ctx.Err()
	}
}

func (client *Client) InitPacketLoss() error {
	id := client.id
	payload := append(hiFormat, 0x20)
	payload = append(payload, []byte(id)...)
	err := client.Write(payload)
	if err != nil {
		return err
	}
	return client.Write(initPacket)
}

type PLoss struct {
	Sent int `json:"sent"`
	Dup  int `json:"dup"`
	Max  int `json:"max"`
}

func (p PLoss) String() string {
	if p.Sent == 0 {
		return "Packet Loss: N/A"
	}
	return fmt.Sprintf("Packet Loss: %.2f%% (Sent: %d/Dup: %d/Max: %d)", p.Loss()*100, p.Sent, p.Dup, p.Max)
}

func (p PLoss) Loss() float64 {
	if p.Sent == 0 {
		return -1
	}
	return 1 - (float64(p.Sent-p.Dup))/float64(p.Max+1)
}

func (p PLoss) LossPercent() float64 {
	if p.Sent == 0 {
		return -1
	}
	return p.Loss() * 100
}

func (client *Client) PacketLoss() (*PLoss, error) {
	err := client.Write(packetLoss)
	if err != nil {
		return nil, err
	}
	result, err := client.Read()
	if err != nil {
		return nil, err
	}
	splitResult := bytes.Split(result, []byte{0x20})
	if len(splitResult) < 3 || !bytes.Equal(splitResult[0], packetLoss) {
		return nil, nil
	}
	x0, err := strconv.Atoi(string(splitResult[1]))
	if err != nil {
		return nil, err
	}
	x1, err := strconv.Atoi(string(splitResult[2]))
	if err != nil {
		return nil, err
	}
	x2, err := strconv.Atoi(string(bytes.TrimRight(splitResult[3], "\n")))
	if err != nil {
		return nil, err
	}
	return &PLoss{
		Sent: x0,
		Dup:  x1,
		Max:  x2,
	}, nil
}

func (client *Client) Download() {
	panic("Unimplemented method: Client.Download()")
}

func (client *Client) Upload() {
	panic("Unimplemented method: Client.Upload()")
}
