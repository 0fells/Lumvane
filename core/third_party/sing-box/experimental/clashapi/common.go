package clashapi

import (
	"net/http"
	"net/url"

	"github.com/go-chi/chi/v5"
)

func getEscapeParam(r *http.Request, paramName string) string {
	param := chi.URLParam(r, paramName)
	if newParam, err := url.PathUnescape(param); err == nil {
		param = newParam
	}
	return param
}
