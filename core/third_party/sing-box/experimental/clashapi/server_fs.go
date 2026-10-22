package clashapi

import "net/http"

type Dir http.Dir

func (d Dir) Open(name string) (http.File, error) {
	file, err := http.Dir(d).Open(name)
	if err != nil {
		return nil, err
	}
	return &fileWrapper{file}, nil
}

type fileWrapper struct {
	http.File
}
